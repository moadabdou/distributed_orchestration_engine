package com.doe.manager.server;

import com.doe.core.event.EngineEventListener;
import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.EventsConnection;
import com.doe.core.model.WorkerConnection;
import com.doe.core.protocol.Message;
import com.doe.core.protocol.MessageType;
import com.doe.core.protocol.ProtocolDecoder;
import com.doe.core.protocol.ProtocolEncoder;
import com.doe.core.registry.WorkerRegistry;
import com.doe.core.registry.JobRegistry;
import com.doe.core.registry.WorkerDeathListener;
import com.doe.manager.scheduler.DagScheduler;
import com.doe.manager.scheduler.JobScheduler;
import com.doe.manager.scheduler.JobTimeoutMonitor;
import com.doe.manager.workflow.XComService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    private final DagScheduler dagScheduler;
    private final JobScheduler jobScheduler;
    private final JobTimeoutMonitor jobTimeoutMonitor;
    private final EngineEventListener eventListener;
    private final XComService xComService;
    private final List<WorkerDeathListener> workerDeathListeners = new CopyOnWriteArrayList<>();
    private final int defaultWorkerMaxCapacity;

    private final ConcurrentMap<UUID, ConcurrentMap<String, UUID>> ownershipRegistry = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, Set<EventsConnection>>> subscriptionRegistry = new ConcurrentHashMap<>();

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
            @Value("${manager.worker.default-max-capacity:4}") int defaultWorkerMaxCapacity,
            WorkerRegistry registry,
            JobRegistry jobRegistry,
            DagScheduler dagScheduler,
            JobScheduler jobScheduler,
            JobTimeoutMonitor jobTimeoutMonitor,
            EngineEventListener eventListener,
            XComService xComService,
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
        this.dagScheduler = dagScheduler;
        this.jobScheduler = jobScheduler;
        this.jobTimeoutMonitor = jobTimeoutMonitor;
        this.eventListener = eventListener;
        this.xComService = xComService;
        this.jwtSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.defaultWorkerMaxCapacity = defaultWorkerMaxCapacity;
        
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
            dagScheduler.start();
            
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
                        .name("connection-init-", Thread.currentThread().threadId())
                        .start(() -> routeConnection(clientSocket));
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

    private void routeConnection(Socket socket) {
        try {
            var inputStream = socket.getInputStream();
            Message firstMessage = ProtocolDecoder.decode(inputStream);

            if (firstMessage.type() == MessageType.REGISTER_WORKER) {
                Thread.currentThread().setName("worker-handler-" + Thread.currentThread().threadId());
                handleWorkerSession(socket, firstMessage);
            } else if (firstMessage.type() == MessageType.REGISTER_JOB_EVENTS) {
                Thread.currentThread().setName("events-handler-" + Thread.currentThread().threadId());
                handleEventsSession(socket, firstMessage);
            } else {
                LOG.warn("Unexpected first message type from {}: {}. Closing connection.",
                        socket.getRemoteSocketAddress(), firstMessage.type());
                socket.close();
            }
        } catch (EOFException e) {
            LOG.info("Client disconnected immediately (stream closed) before sending registration");
            try { socket.close(); } catch (IOException ignored) {}
        } catch (IOException e) {
            LOG.error("I/O error accepting initial connection", e);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Handles a single worker connection: processes the initial registration message,
     * then reads further messages in a loop.
     */
    private void handleWorkerSession(Socket socket, Message firstMessage) {
        UUID workerId = null;
        WorkerConnection localConnection = null;
        try (socket) {
            var inputStream = socket.getInputStream();
            
            // Process the first message immediately
            localConnection = handleRegistration(firstMessage, socket);
            workerId = localConnection.getId();

            while (running && !socket.isClosed()) {
                Message message = ProtocolDecoder.decode(inputStream);

                switch (message.type()) {
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
                    case JOB_LOG -> {
                        if (localConnection != null) {
                            handleJobLog(workerId, localConnection, message);
                        } else {
                            LOG.warn("Received JOB_LOG from unregistered connection {}",
                                    socket.getRemoteSocketAddress());
                        }
                    }
                    case XCOM_REQUEST -> {
                        if (localConnection != null) {
                            handleXComRequest(workerId, localConnection, message);
                        } else {
                            LOG.warn("Received XCOM_REQUEST from unregistered connection {}",
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
                // Take snapshot of active jobs before unregistering
                java.util.Set<UUID> activeJobsSnapshot = new java.util.HashSet<>(localConnection.getActiveJobs());

                // Conditional remove: only evict if WE are still the registered connection.
                // If a newer thread re-registered with the same UUID, leave its entry alone.
                boolean removed = registry.unregisterIfSame(workerId, localConnection);
                if (removed) {
                    LOG.info("Worker {} removed from registry (TCP disconnect)", workerId);
                    // Notify DB: TCP disconnect → worker is offline
                    eventListener.onWorkerDied(workerId);
                    UUID finalId = workerId;
                    workerDeathListeners.forEach(l -> l.onWorkerDeath(finalId, activeJobsSnapshot));
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

        WorkerConnection connection = new WorkerConnection(workerId, socket, defaultWorkerMaxCapacity);

        // Set MDC for all subsequent log statements in this handler thread
        MDC.put("workerId", workerId.toString());

        LOG.info("Worker connected from {} (hostname: {})", connection.getRemoteAddress(), hostname);

        Optional<WorkerConnection> prevOpt = registry.get(workerId);
        if (prevOpt.isPresent()) {
            WorkerConnection prev = prevOpt.get();
            java.util.Set<UUID> activeJobsSnapshot = new java.util.HashSet<>(prev.getActiveJobs());
            LOG.warn("Worker {} reconnected. Evicting previous stale connection.", workerId);
            try { prev.getSocket().close(); } catch (Exception ignored) {}
            if (registry.unregisterIfSame(workerId, prev)) {
                eventListener.onWorkerDied(workerId);
                UUID finalId = workerId;
                workerDeathListeners.forEach(l -> l.onWorkerDeath(finalId, activeJobsSnapshot));
            }
        }

        // Persist worker registration — extract IP from socket
        String ipAddress = (socket.getRemoteSocketAddress() instanceof InetSocketAddress addr)
                ? addr.getHostString() : "unknown";
        eventListener.onWorkerRegistered(workerId, hostname, ipAddress, connection.getMaxCapacity(), connection.getConnectedAt());

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
        // LOG.debug("Heartbeat received from Worker {}", workerId);
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
        JsonObject json = GSON.fromJson(message.payloadAsString(), JsonObject.class);
        if (!json.has("jobId")) {
            LOG.warn("Worker {}: message missing jobId", workerId);
            return;
        }
        UUID jobId = UUID.fromString(json.get("jobId").getAsString());
        Job job = jobRegistry.get(jobId).orElse(null);
        
        if (job == null) {
            LOG.warn("Worker {}: received JOB_RUNNING for unknown job {}", workerId, jobId);
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
        String summary = json.has("output") ? json.get("output").getAsString() : (json.has("summary") ? json.get("summary").getAsString() : "");
        List<String> logs = json.has("logs") ? GSON.fromJson(json.get("logs"), new com.google.gson.reflect.TypeToken<List<String>>(){}.getType()) : List.of();
        
        if (!json.has("jobId")) {
            LOG.warn("Worker {}: message missing jobId", workerId);
            return;
        }
        UUID jobId = UUID.fromString(json.get("jobId").getAsString());
        Job job = jobRegistry.get(jobId).orElse(null);

        if (job == null) {
            LOG.warn("Worker {}: received JOB_RESULT for unknown job {}", workerId, jobId);
            return;
        }

        if (!workerId.equals(job.getAssignedWorkerId())) {
            LOG.warn("Worker {}: ignored JOB_RESULT for job {} (no longer assigned to this worker)", workerId, job.getId());
            registry.releaseCapacity(workerId, jobId);
            return;
        }

        // 1. Save logs to file (Overwrites any previous incremental logs with the final ground truth)
        saveJobLogs(jobId, logs, false);

        job.setResult(summary);
        try {
            JobStatus target;
            if ("COMPLETED".equals(status)) {
                target = JobStatus.COMPLETED;
            } else if ("CANCELLED".equals(status)) {
                target = JobStatus.CANCELLED;
            } else {
                target = JobStatus.FAILED;
            }
            
            job.transition(target);
            LOG.info("Worker {}: job {} → {} | summary: {}", workerId, job.getId(), target, summary);

            long durationMs = java.time.Duration.between(job.getCreatedAt(), job.getUpdatedAt()).toMillis();
            LOG.info("Job {} {} by Worker {} in {}ms", job.getId(), target, workerId, durationMs);

            registry.releaseCapacity(workerId, job.getId());

            // Persist the final job state to DB
            if (target == JobStatus.COMPLETED) {
                eventListener.onJobCompleted(job.getId(), workerId, summary, job.getUpdatedAt());
            } else if (target == JobStatus.CANCELLED) {
                eventListener.onJobCancelled(job.getId(), workerId, summary, job.getUpdatedAt());
            } else {
                eventListener.onJobFailed(job.getId(), workerId, summary, job.getUpdatedAt());
            }
        } catch (IllegalStateException e) {
            LOG.warn("Worker {}: could not transition job {} to terminal state: {}", workerId, job.getId(), e.getMessage());
            registry.releaseCapacity(workerId, job.getId());
        }
    }

    void handleXComRequest(UUID workerId, WorkerConnection localConnection, Message message) {
        try {
            JsonObject json = GSON.fromJson(message.payloadAsString(), JsonObject.class);
            String correlationId = json.get("correlationId").getAsString();
            UUID jobId = UUID.fromString(json.get("jobId").getAsString());
            String command = json.get("command").getAsString();
            String key = json.get("key").getAsString();

            // Find job to get workflowId
            Job job = jobRegistry.get(jobId).orElse(null);
            if (job == null) {
                sendXComResponse(localConnection, correlationId, "ERROR", "Job not found: " + jobId);
                return;
            }
            
            UUID workflowId = null; 
            if (json.has("workflowId")) {
                workflowId = UUID.fromString(json.get("workflowId").getAsString());
            }

            if (workflowId == null) {
                sendXComResponse(localConnection, correlationId, "ERROR", "workflowId missing in request");
                return;
            }

            if ("push".equalsIgnoreCase(command)) {
                String value = json.get("value").getAsString();
                String type = json.has("type") ? json.get("type").getAsString() : "message";
                xComService.push(workflowId, jobId, key, value, type);
                sendXComResponse(localConnection, correlationId, "SUCCESS", "Pushed");
            } else if ("pull".equalsIgnoreCase(command)) {
                Optional<String> val = xComService.pull(workflowId, key);
                if (val.isPresent()) {
                    sendXComResponse(localConnection, correlationId, "SUCCESS", val.get());
                } else {
                    sendXComResponse(localConnection, correlationId, "NOT_FOUND", null);
                }
            } else {
                sendXComResponse(localConnection, correlationId, "ERROR", "Unknown command: " + command);
            }

        } catch (Exception e) {
            LOG.error("Error handling XCom request from worker {}", workerId, e);
        }
    }

    void sendXComResponse(WorkerConnection connection, String correlationId, String status, String value) {
        try {
            JsonObject response = new JsonObject();
            response.addProperty("correlationId", correlationId);
            response.addProperty("status", status);
            if (value != null) {
                response.addProperty("value", value);
            }
            byte[] wire = ProtocolEncoder.encode(MessageType.XCOM_RESPONSE, GSON.toJson(response));
            connection.getSocket().getOutputStream().write(wire);
            connection.getSocket().getOutputStream().flush();
        } catch (IOException e) {
            LOG.error("Failed to send XCOM_RESPONSE back to worker", e);
        }
    }

    private void handleJobLog(UUID workerId, WorkerConnection localConnection, Message message) {
        try {
            JsonObject json = GSON.fromJson(message.payloadAsString(), JsonObject.class);
            if (!json.has("jobId") || !json.has("logs")) return;
            
            UUID jobId = UUID.fromString(json.get("jobId").getAsString());
            List<String> logs = GSON.fromJson(json.get("logs"), new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
            
            saveJobLogs(jobId, logs, true);
        } catch (Exception e) {
            LOG.error("Worker {}: failed to process JOB_LOG", workerId, e);
        }
    }

    private void saveJobLogs(UUID jobId, List<String> logs, boolean append) {
        try {
            Path logDir = Paths.get("data", "var", "logs", "jobs");
            Files.createDirectories(logDir);
            Path logFile = logDir.resolve(jobId.toString() + ".log");
            
            if (append && Files.exists(logFile)) {
                // Read current logs, append new ones, and write back as JSON array
                // For performance, we could use a different format (e.g. newline-delimited JSON), 
                // but we'll stick to the current JSON array format for compatibility.
                String existingContent = Files.readString(logFile, StandardCharsets.UTF_8);
                List<String> existingLogs = GSON.fromJson(existingContent, new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
                existingLogs.addAll(logs);
                Files.writeString(logFile, GSON.toJson(existingLogs), StandardCharsets.UTF_8);
            } else {
                Files.writeString(logFile, GSON.toJson(logs), StandardCharsets.UTF_8);
            }
            LOG.debug("{} logs for job {} to {}", append ? "Appended" : "Saved", jobId, logFile);
        } catch (IOException e) {
            LOG.error("Failed to save logs for job {}", jobId, e);
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
                        
                        // Take snapshot of active jobs before unregistering
                        java.util.Set<UUID> activeJobsSnapshot = new java.util.HashSet<>(worker.getActiveJobs());

                        // Remove from registry immediately to prevent relying on the handler thread's finally block
                        boolean removed = registry.unregisterIfSame(worker.getId(), worker);

                        if (removed) {
                            // Notify listeners (e.g., CrashRecoveryHandler) and persist to DB
                            LOG.info("Worker {} removed from registry (heartbeat timeout)", worker.getId());
                            eventListener.onWorkerDied(worker.getId());
                            workerDeathListeners.forEach(l -> l.onWorkerDeath(worker.getId(), activeJobsSnapshot));
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
        dagScheduler.stop();

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
     * Sends a CANCEL_JOB message to the specified worker for the given job.
     *
     * @param workerId the ID of the worker executing the job
     * @param jobId    the ID of the job to abort
     */
    public void sendCancelJob(UUID workerId, UUID jobId) {
        registry.get(workerId).ifPresentOrElse(connection -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("jobId", jobId.toString());
                byte[] wire = ProtocolEncoder.encode(MessageType.CANCEL_JOB, GSON.toJson(json));
                connection.getSocket().getOutputStream().write(wire);
                connection.getSocket().getOutputStream().flush();
                LOG.info("ManagerServer: Sent CANCEL_JOB to worker {} targeting job {}", workerId, jobId);
            } catch (Exception e) {
                LOG.error("ManagerServer: Failed to dispatch CANCEL_JOB to worker {}", workerId, e);
            }
        }, () -> {
            LOG.warn("ManagerServer: Worker {} not found, couldn't issue CANCEL_JOB for {}", workerId, jobId);
        });
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

    private void handleEventsSession(Socket socket, Message firstMessage) {
        UUID jobId = null;
        UUID workflowId = null;
        EventsConnection connection = null;

        try {
            var in = socket.getInputStream();
            var out = socket.getOutputStream();

            JsonObject json = GSON.fromJson(firstMessage.payloadAsString(), JsonObject.class);
            String authToken = json.has("auth_token") ? json.get("auth_token").getAsString() : null;
            if (authToken == null || authToken.isBlank()) {
                throw new IOException("Missing auth_token in event session");
            }
            
            try {
                String subject = Jwts.parser()
                        .verifyWith(jwtSecretKey)
                        .build()
                        .parseSignedClaims(authToken)
                        .getPayload()
                        .getSubject();
                jobId = UUID.fromString(subject);
            } catch (JwtException | IllegalArgumentException e) {
                throw new IOException("Invalid auth_token", e);
            }

            Job job = jobRegistry.get(jobId).orElse(null);
            if (job == null) {
                throw new IOException("Job not found: " + jobId);
            }
            workflowId = job.getWorkflowId();
            
            connection = new EventsConnection(workflowId, jobId, socket);
            MDC.put("jobId", jobId.toString());
            MDC.put("workflowId", workflowId.toString());

            JsonObject ack = new JsonObject();
            ack.addProperty("status", "events-registered");
            ack.addProperty("jobId", jobId.toString());
            out.write(ProtocolEncoder.encode(MessageType.REGISTER_ACK, GSON.toJson(ack)));
            out.flush();

            LOG.info("Events Session started for Job {} in Workflow {}", jobId, workflowId);

            while (running && !socket.isClosed()) {
                Message msg = ProtocolDecoder.decode(in);
                JsonObject payload = GSON.fromJson(msg.payloadAsString(), JsonObject.class);
                
                switch (msg.type()) {
                    case EVENT_REGISTER -> {
                        String eventName = payload.get("eventName").getAsString();
                        var workflowEvents = ownershipRegistry.computeIfAbsent(workflowId, k -> new ConcurrentHashMap<>());
                        UUID existingOwner = workflowEvents.putIfAbsent(eventName, jobId);
                        if (existingOwner != null && !existingOwner.equals(jobId)) {
                            LOG.warn("Job {} tried to register event '{}' but owned by {}", jobId, eventName, existingOwner);
                        } else {
                            LOG.debug("Job {} registered event '{}'", jobId, eventName);
                        }
                    }
                    case EVENT_SUBSCRIBE -> {
                        String eventName = payload.get("eventName").getAsString();
                        subscriptionRegistry
                            .computeIfAbsent(workflowId, k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(eventName, k -> ConcurrentHashMap.newKeySet())
                            .add(connection);
                        LOG.debug("Job {} subscribed to '{}'", jobId, eventName);
                    }
                    case EVENT_PUBLISH -> {
                        String eventName = payload.get("eventName").getAsString();
                        var workflowEvents = ownershipRegistry.get(workflowId);
                        if (workflowEvents == null || !jobId.equals(workflowEvents.get(eventName))) {
                            LOG.warn("Job {} attempted to publish unowned event '{}'", jobId, eventName);
                            continue;
                        }

                        var subscribersMap = subscriptionRegistry.get(workflowId);
                        if (subscribersMap != null) {
                            Set<EventsConnection> subscribers = subscribersMap.get(eventName);
                            if (subscribers != null && !subscribers.isEmpty()) {
                                byte[] notifyBytes = ProtocolEncoder.encode(MessageType.EVENT_NOTIFY, msg.payloadAsString());
                                for (EventsConnection sub : subscribers) {
                                    try {
                                        sub.getSocket().getOutputStream().write(notifyBytes);
                                        sub.getSocket().getOutputStream().flush();
                                    } catch (IOException e) {
                                        LOG.error("Failed to notify subscriber Job {}", sub.getJobId(), e);
                                    }
                                }
                            }
                        }
                    }
                    default -> LOG.warn("Unexpected message type through Events channel: {}", msg.type());
                }
            }
        } catch (EOFException e) {
            LOG.debug("Events session disconnected (stream closed)");
        } catch (SocketException e) {
            if (running) {
                LOG.debug("Events session connection reset: {}", e.getMessage());
            }
        } catch (IOException e) {
            LOG.error("I/O error in Events channel", e);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            if (workflowId != null && connection != null) {
                var wfSubs = subscriptionRegistry.get(workflowId);
                if (wfSubs != null) {
                    for (var eventSubs : wfSubs.values()) {
                        eventSubs.remove(connection);
                    }
                }
            }
            MDC.clear();
        }
    }
}
