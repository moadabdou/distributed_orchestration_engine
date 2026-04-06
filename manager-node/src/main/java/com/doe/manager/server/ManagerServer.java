package com.doe.manager.server;

import com.doe.core.event.EngineEventListener;
import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkerConnection;
import com.doe.core.protocol.Message;
import com.doe.core.protocol.MessageType;
import com.doe.core.protocol.ProtocolDecoder;
import com.doe.core.protocol.ProtocolEncoder;
import com.doe.core.registry.WorkerRegistry;
import com.doe.core.registry.JobRegistry;
import com.doe.core.registry.WorkerDeathListener;
import com.doe.manager.scheduler.JobScheduler;
import com.doe.manager.scheduler.JobTimeoutMonitor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

/**
 * Central manager server that accepts worker TCP connections using Java 21 Virtual Threads.
 * <p>
 * Each accepted connection is handled in a dedicated Virtual Thread. The server
 * maintains a thread-safe {@link WorkerRegistry} to track connected workers.
 * <p>
 * Wire protocol: {@code [1B Type][4B Length][NB Payload]}
 */
@Service
public class ManagerServer implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ManagerServer.class);
    private static final Gson GSON = new Gson();

    private final int port;
    private final WorkerRegistry registry;
    private final JobRegistry jobRegistry;
    private final long heartbeatCheckIntervalMs;
    private final long heartbeatTimeoutMs;
    private final JobScheduler jobScheduler;
    private final JobTimeoutMonitor jobTimeoutMonitor;
    private final EngineEventListener eventListener;
    private final List<WorkerDeathListener> workerDeathListeners = new CopyOnWriteArrayList<>();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private ScheduledExecutorService monitorExecutor;
    private final SecretKey jwtSecretKey;

    /**
     * Creates a new ManagerServer using Spring constructor injection.
     */
    public ManagerServer(
            @Value("${server.tcp.port:9090}") int port,
            @Value("${fernos.heartbeat.check.interval:5000}") long heartbeatCheckIntervalMs,
            @Value("${fernos.heartbeat.timeout:15000}") long heartbeatTimeoutMs,
            @Value("${manager.security.jwt.secret:default-secret}") String jwtSecret,
            WorkerRegistry registry,
            JobRegistry jobRegistry,
            JobScheduler jobScheduler,
            JobTimeoutMonitor jobTimeoutMonitor,
            EngineEventListener eventListener,
            List<WorkerDeathListener> workerDeathListeners) {
            
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535, got: " + port);
        }
        if (heartbeatCheckIntervalMs <= 0 || heartbeatTimeoutMs <= 0) {
            throw new IllegalArgumentException("Heartbeat intervals must be positive");
        }
        
        this.port = port;
        this.heartbeatCheckIntervalMs = heartbeatCheckIntervalMs;
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
        this.registry = registry;
        this.jobRegistry = jobRegistry;
        this.jobScheduler = jobScheduler;
        this.jobTimeoutMonitor = jobTimeoutMonitor;
        this.eventListener = eventListener;
        this.jwtSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        
        if (workerDeathListeners != null) {
            this.workerDeathListeners.addAll(workerDeathListeners);
        }
    }

    /**
     * Starts the server, binding to the configured port and starting the accept loop
     * in a separate virtual thread. Implements SmartLifecycle.
     */
    @Override
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            running = true;

            LOG.info("ManagerServer started on TCP port {}", serverSocket.getLocalPort());

            startHeartbeatMonitor();
            jobTimeoutMonitor.start();
            jobScheduler.start();
            
            Thread.ofVirtual().name("manager-acceptor").start(this::acceptLoop);
        } catch (IOException e) {
            LOG.error("Failed to start ManagerServer", e);
            throw new RuntimeException("Failed to start ManagerServer", e);
        }
    }
    
    private void acceptLoop() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                Thread.ofVirtual()
                        .name("worker-handler-", Thread.currentThread().threadId())
                        .start(() -> handleWorker(clientSocket));
            } catch (SocketException e) {
                if (running) {
                    LOG.error("Error accepting connection", e);
                }
                // If !running, this is expected from shutdown() closing the ServerSocket
            } catch (IOException e) {
                if (running) {
                    LOG.error("I/O error accepting connection", e);
                }
            }
        }
    }

    /**
     * Handles a single worker connection: reads messages in a loop and dispatches by type.
     * <p>
     * Each handler thread tracks its own {@link WorkerConnection} reference so that
     * cleanup in the {@code finally} block only removes <em>its own</em> registry entry,
     * preventing a stale thread from evicting a newer re-registration.
     */
    private void handleWorker(Socket socket) {
        UUID workerId = null;
        WorkerConnection localConnection = null;
        try (socket) {
            var inputStream = socket.getInputStream();

            while (running && !socket.isClosed()) {
                Message message = ProtocolDecoder.decode(inputStream);

                switch (message.type()) {
                    case REGISTER_WORKER -> {
                        localConnection = handleRegistration(message, socket);
                        workerId = localConnection.getId();
                        MDC.put("workerId", workerId.toString());
                    }
                    case HEARTBEAT -> {
                        if (workerId != null) {
                            handleHeartbeat(workerId, localConnection);
                        } else {
                            LOG.warn("Received HEARTBEAT from unregistered connection {}",
                                    socket.getRemoteSocketAddress());
                        }
                    }
                    case JOB_RUNNING -> {
                        if (localConnection != null) {
                            handleJobRunning(workerId, localConnection, message);
                        } else {
                            LOG.warn("Received JOB_RUNNING from unregistered connection {}",
                                    socket.getRemoteSocketAddress());
                        }
                    }
                    case JOB_RESULT -> {
                        if (localConnection != null) {
                            handleJobResult(workerId, localConnection, message);
                        } else {
                            LOG.warn("Received JOB_RESULT from unregistered connection {}",
                                    socket.getRemoteSocketAddress());
                        }
                    }
                    default -> LOG.warn("Unexpected message type: {}", message.type());
                }
            }
        } catch (EOFException e) {
            LOG.info("Worker {} disconnected (stream closed)", workerId != null ? workerId : "unknown");
        } catch (SocketException e) {
            if (running) {
                LOG.info("Worker {} connection reset: {}", workerId != null ? workerId : "unknown", e.getMessage());
            }
        } catch (IOException e) {
            LOG.error("I/O error handling worker {}", workerId != null ? workerId : "unknown", e);
        } finally {
            if (workerId != null && localConnection != null) {
                // Conditional remove: only evict if WE are still the registered connection.
                // If a newer thread re-registered with the same UUID, leave its entry alone.
                boolean removed = registry.unregisterIfSame(workerId, localConnection);
                if (removed) {
                    LOG.info("Worker {} removed from registry (TCP disconnect)", workerId);
                    // Notify DB: TCP disconnect → worker is offline
                    eventListener.onWorkerDied(workerId);
                    UUID finalId = workerId;
                    workerDeathListeners.forEach(l -> l.onWorkerDeath(finalId));
                } else {
                    LOG.debug("Worker {} was already {}", workerId, registry.get(workerId).isPresent() ? "replaced by a newer connection; skipping removal" : "removed by another thread; skipping removal");
                }
            }
            MDC.clear();
        }
    }

    /**
     * Processes a REGISTER_WORKER message: assigns a manager-generated UUID,
     * creates a {@link WorkerConnection}, registers it, and sends back a REGISTER_ACK
     * containing the assigned UUID.
     * <p>
     * <b>Security:</b> The manager always generates the UUID server-side.
     * Any client-supplied {@code workerId} in the payload is ignored. This
     * prevents UUID spoofing attacks where a malicious client could forge
     * registrations to evict legitimate workers.
     *
     * @return the newly created {@link WorkerConnection}
     */
    private WorkerConnection handleRegistration(Message message, Socket socket) throws IOException {
        // Parse payload for metadata and auth_token
        JsonObject json = GSON.fromJson(message.payloadAsString(), JsonObject.class);
        String hostname = json.has("hostname") ? json.get("hostname").getAsString() : "unknown";
        String authToken = json.has("auth_token") ? json.get("auth_token").getAsString() : null;

        if (authToken == null || authToken.isBlank()) {
            LOG.warn("Registration rejected from {}: missing auth_token", socket.getRemoteSocketAddress());
            throw new IOException("Missing auth_token");
        }

        UUID workerId;
        try {
            String subject = Jwts.parser()
                    .verifyWith(jwtSecretKey)
                    .build()
                    .parseSignedClaims(authToken)
                    .getPayload()
                    .getSubject();
            
            workerId = UUID.fromString(subject);
        } catch (JwtException | IllegalArgumentException e) {
            LOG.warn("Registration rejected from {}: invalid auth_token - {}", socket.getRemoteSocketAddress(), e.getMessage());
            throw new IOException("Invalid auth_token", e);
        }

        WorkerConnection connection = new WorkerConnection(workerId, socket);

        LOG.info("Worker {} connected from {} (hostname: {})", workerId, connection.getRemoteAddress(), hostname);

        Optional<WorkerConnection> prevOpt = registry.get(workerId);
        if (prevOpt.isPresent()) {
            WorkerConnection prev = prevOpt.get();
            LOG.warn("Worker {} reconnected. Evicting previous stale connection.", workerId);
            try { prev.getSocket().close(); } catch (Exception ignored) {}
            if (registry.unregisterIfSame(workerId, prev)) {
                eventListener.onWorkerDied(workerId);
                UUID finalId = workerId;
                workerDeathListeners.forEach(l -> l.onWorkerDeath(finalId));
            }
        }

        // Persist worker registration — extract IP from socket
        String ipAddress = (socket.getRemoteSocketAddress() instanceof InetSocketAddress addr)
                ? addr.getHostString() : "unknown";
        eventListener.onWorkerRegistered(workerId, hostname, ipAddress, connection.getConnectedAt());

        // Make worker available to scheduler only after successful DB persistence
        registry.register(connection);

        // Send REGISTER_ACK with the manager-assigned UUID
        JsonObject ackPayload = new JsonObject();
        ackPayload.addProperty("workerId", workerId.toString());
        ackPayload.addProperty("status", "registered");

        byte[] ackBytes = ProtocolEncoder.encode(MessageType.REGISTER_ACK, GSON.toJson(ackPayload));
        OutputStream out = socket.getOutputStream();
        out.write(ackBytes);
        out.flush();

        return connection;
    }

    /**
     * Processes a HEARTBEAT message: updates the worker's last-seen timestamp.
     * <p>
     * Uses the handler's local {@link WorkerConnection} reference directly,
     * rather than looking up the registry, to avoid updating a different
     * connection if a re-registration occurred concurrently.
     */
    private void handleHeartbeat(UUID workerId, WorkerConnection localConnection) {
        localConnection.updateHeartbeat();
        LOG.debug("Heartbeat received from Worker {}", workerId);
        eventListener.onWorkerHeartbeat(workerId, localConnection.getLastHeartbeat());
    }

    /**
     * Processes a JOB_RUNNING message: transitions the current job ASSIGNED → RUNNING.
     * <p>
     * The manager receives this immediately after sending ASSIGN_JOB, confirming
     * that the worker has started execution. This distinguishes "assigned but
     * not yet started" from "actively executing".
     */
    private void handleJobRunning(UUID workerId, WorkerConnection localConnection, Message message) {
        Job job = localConnection.getCurrentJob();
        if (job == null) {
            LOG.warn("Worker {}: received JOB_RUNNING but no current job tracked", workerId);
            return;
        }
        if (!workerId.equals(job.getAssignedWorkerId())) {
            LOG.warn("Worker {}: ignored JOB_RUNNING for job {} (no longer assigned to this worker)", workerId, job.getId());
            return;
        }
        try {
            job.transition(JobStatus.RUNNING);
            LOG.info("Worker {}: job {} transitioned ASSIGNED → RUNNING", workerId, job.getId());
            eventListener.onJobRunning(job.getId(), job.getUpdatedAt());
        } catch (IllegalStateException e) {
            LOG.warn("Worker {}: could not transition job {} to RUNNING: {}", workerId, job.getId(), e.getMessage());
        }
    }

    /**
     * Processes a JOB_RESULT message: records the output, transitions the job
     * to COMPLETED or FAILED, and marks the worker IDLE again.
     */
    private void handleJobResult(UUID workerId, WorkerConnection localConnection, Message message) {
        JsonObject json = GSON.fromJson(message.payloadAsString(), JsonObject.class);
        String status = json.has("status") ? json.get("status").getAsString() : "FAILED";
        String output = json.has("output") ? json.get("output").getAsString() : "";

        Job job = localConnection.getCurrentJob();
        if (job == null) {
            LOG.warn("Worker {}: received JOB_RESULT but no current job tracked", workerId);
            return;
        }

        if (!workerId.equals(job.getAssignedWorkerId())) {
            LOG.warn("Worker {}: ignored JOB_RESULT for job {} (no longer assigned to this worker)", workerId, job.getId());
            registry.markIdle(workerId);
            return;
        }

        job.setResult(output);
        try {
            JobStatus target = "COMPLETED".equals(status)
                    ? JobStatus.COMPLETED
                    : JobStatus.FAILED;
            job.transition(target);
            LOG.info("Worker {}: job {} → {} | output: {}", workerId, job.getId(), target, output);

            long durationMs = java.time.Duration.between(job.getCreatedAt(), job.getUpdatedAt()).toMillis();
            LOG.info("Job {} {} by Worker {} in {}ms", job.getId(), target, workerId, durationMs);

            // Persist the final job state to DB
            if (target == JobStatus.COMPLETED) {
                eventListener.onJobCompleted(job.getId(), output, job.getUpdatedAt());
            } else {
                eventListener.onJobFailed(job.getId(), output, job.getUpdatedAt());
            }
        } catch (IllegalStateException e) {
            LOG.warn("Worker {}: could not transition job {} to terminal state: {}", workerId, job.getId(), e.getMessage());
        } finally {
            // markIdle() clears currentJob and re-offers the worker to the idle queue
            registry.markIdle(workerId);
            LOG.info("Worker {}: marked IDLE after job {}", workerId, job.getId());
            eventListener.onWorkerIdle(workerId);
        }
    }

    /**
     * Starts the scheduled HeartbeatMonitor task.
     */
    private void startHeartbeatMonitor() {
        monitorExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "heartbeat-monitor");
            t.setDaemon(true);
            return t;
        });

        monitorExecutor.scheduleAtFixedRate(() -> {
            try {
                long now = Instant.now().toEpochMilli();
                registry.getAll().values().forEach(worker -> {
                    long elapsed = now - worker.getLastHeartbeat().toEpochMilli();
                    if (elapsed > heartbeatTimeoutMs) {
                        LOG.warn("Worker {} marked DEAD (no heartbeat for {} ms)", worker.getId(), elapsed);
                        try {
                            if (!worker.getSocket().isClosed()) {
                                worker.getSocket().close();
                            }
                        } catch (IOException e) {
                            LOG.warn("Error closing dead worker {} socket", worker.getId(), e);
                        }
                        // Remove from registry immediately to prevent relying on the handler thread's finally block
                        boolean removed = registry.unregisterIfSame(worker.getId(), worker);

                        if (removed) {
                            // Notify listeners (e.g., CrashRecoveryHandler) and persist to DB
                            LOG.info("Worker {} removed from registry (heartbeat timeout)", worker.getId());
                            eventListener.onWorkerDied(worker.getId());
                            workerDeathListeners.forEach(l -> l.onWorkerDeath(worker.getId()));
                        } else {
                            LOG.warn("Worker {} was already removed from registry", worker.getId());
                        }
                    }
                });
            } catch (Exception e) {
                LOG.error("Unexpected error in heartbeat monitor", e);
            }
        }, heartbeatCheckIntervalMs, heartbeatCheckIntervalMs, TimeUnit.MILLISECONDS);
        
        LOG.info("HeartbeatMonitor started (interval: {} ms, timeout: {} ms)", 
                heartbeatCheckIntervalMs, heartbeatTimeoutMs);
    }

    @Override
    public void stop() {
        shutdown();
    }

    /**
     * Gracefully shuts down the server: closes the server socket and all worker connections.
     */
    public void shutdown() {
        LOG.info("Shutting down ManagerServer...");
        running = false;

        jobTimeoutMonitor.stop();
        jobScheduler.stop();

        if (monitorExecutor != null && !monitorExecutor.isShutdown()) {
            monitorExecutor.shutdownNow();
        }

        // Close the server socket to break the accept() loop
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.error("Error closing server socket", e);
        }

        // Close all registered worker connections
        registry.getAll().forEach((id, connection) -> {
            try {
                connection.getSocket().close();
            } catch (IOException e) {
                LOG.error("Error closing socket for Worker {}", id, e);
            }
        });

        LOG.info("ManagerServer shut down. Final registry size: {}", registry.size());
    }

    /**
     * Registers a listener to be notified when a worker dies.
     */
    public void addWorkerDeathListener(WorkerDeathListener listener) {
        if (listener != null) {
            workerDeathListeners.add(listener);
        }
    }

    /**
     * Returns the worker registry (primarily for testing).
     */
    public WorkerRegistry getRegistry() {
        return registry;
    }

    /**
     * Returns the job scheduler (primarily for testing).
     */
    public JobScheduler getJobScheduler() {
        return jobScheduler;
    }

    /**
     * Returns the job registry (primarily for testing).
     */
    public JobRegistry getJobRegistry() {
        return jobRegistry;
    }

    /**
     * Returns the actual port the server is listening on.
     * Useful when started with port 0 (OS-assigned).
     *
     * @return the local port, or -1 if not yet started
     */
    public int getLocalPort() {
        return (serverSocket != null) ? serverSocket.getLocalPort() : -1;
    }

    /**
     * Returns whether the server is currently running.
     */
    public boolean isRunning() {
        return running;
    }
}
