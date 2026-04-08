package com.doe.worker.client;

import com.doe.core.protocol.Message;
import com.doe.core.protocol.MessageType;
import com.doe.core.protocol.ProtocolDecoder;
import com.doe.core.protocol.ProtocolEncoder;
import com.doe.core.util.RetryPolicy;
import com.doe.worker.executor.EchoPlugin;
import com.doe.worker.executor.FibonacciPlugin;
import com.doe.worker.executor.ShellScriptPlugin;
import com.doe.worker.executor.SleepPlugin;
import com.doe.worker.executor.TaskPluginRegistry;
import com.doe.worker.executor.UnknownTaskTypeException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


/**
 * The Worker client: establishes a TCP connection to the Manager, registers itself,
 * and enters a main loop to receive commands such as {@code ASSIGN_JOB}.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Connect to Manager at {@code host:port} via TCP (with exponential-backoff retry).</li>
 *   <li>Send {@code REGISTER_WORKER} containing the local hostname.</li>
 *   <li>Read {@code REGISTER_ACK} — the manager assigns the worker UUID.</li>
 *   <li>Enter main loop: block on {@link ProtocolDecoder#decode} waiting for commands.</li>
 *   <li>On disconnect / exception → log, reconnect with backoff.</li>
 * </ol>
 */
public class WorkerClient {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerClient.class);
    private static final Gson GSON = new Gson();

    /** Backoff: 1 s → 2 s → 4 s … capped at 30 s, unlimited attempts. */
    private static final RetryPolicy RETRY_POLICY =
            new RetryPolicy(1_000, 30_000, Integer.MAX_VALUE);

    /** Builds the default registry used when no custom registry is supplied. */
    private static TaskPluginRegistry defaultRegistry() {
        return new TaskPluginRegistry()
                .register("echo",      new EchoPlugin())
                .register("sleep",     new SleepPlugin())
                .register("fibonacci", new FibonacciPlugin())
                .register("bash",      new ShellScriptPlugin());
    }

    private final String host;
    private final int port;
    private final long heartbeatIntervalMs;
    private final int readTimeoutMs;
    private final String authToken;
    private final TaskPluginRegistry registry;
    private final ExecutorService jobExecutor;
    private final ConcurrentHashMap<String, CompletableFuture<?>> activeJobs = new ConcurrentHashMap<>();

    private volatile boolean running = true;
    private volatile Socket socket;

    /**
     * Creates a new WorkerClient with default heartbeat interval (5000ms).
     *
     * @param host the manager hostname or IP address
     * @param port the manager TCP port
     */
    public WorkerClient(String host, int port) {
        this(host, port, 5000, 10000, null, defaultRegistry());
    }

    /**
     * Creates a new WorkerClient.
     *
     * @param host                the manager hostname or IP address
     * @param port                the manager TCP port
     * @param heartbeatIntervalMs heartbeat interval in milliseconds
     */
    public WorkerClient(String host, int port, long heartbeatIntervalMs) {
        this(host, port, heartbeatIntervalMs, 10000, null, defaultRegistry());
    }

    /**
     * Creates a new WorkerClient.
     *
     * @param host                the manager hostname or IP address
     * @param port                the manager TCP port
     * @param heartbeatIntervalMs heartbeat interval in milliseconds
     * @param readTimeoutMs       socket read timeout in milliseconds
     * @param authToken           the JWT authentication token
     */
    public WorkerClient(String host, int port, long heartbeatIntervalMs, int readTimeoutMs, String authToken) {
        this(host, port, heartbeatIntervalMs, readTimeoutMs, authToken, defaultRegistry());
    }

    /**
     * Full constructor — accepts an externally-built {@link TaskPluginRegistry}.
     * Useful in tests to inject a controlled set of plugins.
     */
    public WorkerClient(String host, int port, long heartbeatIntervalMs, int readTimeoutMs,
                        String authToken, TaskPluginRegistry registry) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535, got: " + port);
        }
        if (heartbeatIntervalMs <= 0) {
            throw new IllegalArgumentException("heartbeatIntervalMs must be positive, got: " + heartbeatIntervalMs);
        }
        if (readTimeoutMs <= 0) {
            throw new IllegalArgumentException("readTimeoutMs must be positive, got: " + readTimeoutMs);
        }
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        this.host = host;
        this.port = port;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.readTimeoutMs = readTimeoutMs;
        this.authToken = authToken;
        this.registry = registry;
        this.jobExecutor = Executors.newFixedThreadPool(4);
    }

    /**
     * Starts the worker: connects, registers, and runs the main command loop.
     * <p>
     * On disconnect, automatically reconnects with exponential backoff.
     * Blocks until {@link #shutdown()} is called.
     */
    public void start() {
        LOG.info("WorkerClient starting, targeting manager at {}:{}", host, port);

        while (running) {
            try {
                connectAndRun();
            } catch (ConnectException e) {
                // RetryPolicy exhausted (won't happen with MAX_VALUE, but guard anyway)
                LOG.error("Could not connect to manager at {}:{}: {}", host, port, e.getMessage());
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.info("WorkerClient interrupted, shutting down.");
                break;
            } catch (IOException e) {
                if (running) {
                    LOG.error("Unexpected I/O error, will attempt reconnect.", e);
                    sleepQuietly(2_000);
                }
            }
        }

        LOG.info("WorkerClient stopped.");
    }

    /**
     * One full connection lifecycle: connect → register → main loop.
     * Throws on non-retryable errors; {@link ConnectException} is handled by {@link RetryPolicy}.
     */
    private void connectAndRun() throws IOException, InterruptedException {
        // ── 1. Connect with exponential backoff ───────────────────────────────
        RETRY_POLICY.execute(() -> {
            LOG.info("Connecting to manager at {}:{}...", host, port);
            socket = new Socket(host, port);
            socket.setSoTimeout(readTimeoutMs);
            LOG.info("Connected to manager at {}", socket.getRemoteSocketAddress());
        });

        Thread writerThread = null;
        try (Socket s = socket) {
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            
            BlockingQueue<OutboundMessage> egressQueue = new LinkedBlockingQueue<>();
            writerThread = Thread.ofVirtual().name("egress-writer").start(() -> runWriterLoop(s, out, egressQueue));

            // ── 2. Send REGISTER_WORKER ───────────────────────────────────────
            String hostname = resolveHostname();
            JsonObject regPayload = new JsonObject();
            regPayload.addProperty("hostname", hostname);
            if (authToken != null && !authToken.isBlank()) {
                regPayload.addProperty("auth_token", authToken);
            }

            byte[] regBytes = ProtocolEncoder.encode(
                    MessageType.REGISTER_WORKER, GSON.toJson(regPayload));
            
            egressQueue.put(new OutboundMessage(regBytes, e -> LOG.error("Failed to send REGISTER_WORKER", e)));

            LOG.info("Sent REGISTER_WORKER (hostname: {})", hostname);

            // ── 3. Read REGISTER_ACK — manager assigns our UUID ───────────────
            Message ack = ProtocolDecoder.decode(in);
            if (ack.type() != MessageType.REGISTER_ACK) {
                throw new IOException(
                        "Expected REGISTER_ACK but received: " + ack.type());
            }

            JsonObject ackJson = GSON.fromJson(ack.payloadAsString(), JsonObject.class);

            if (!ackJson.has("workerId")) {
                throw new IOException("REGISTER_ACK missing required 'workerId' field");
            }

            UUID workerId = UUID.fromString(ackJson.get("workerId").getAsString());
            LOG.info("Registered with manager, assigned worker ID: {}", workerId);

            Thread.ofVirtual().name("heartbeat-" + workerId).start(() -> runHeartbeatLoop(s, egressQueue, workerId));

            // ── 4. Main command loop ──────────────────────────────────────────
            runMainLoop(in, egressQueue, workerId);
        }finally {
            if (writerThread != null) {
                // queue.take() blocks, so we need to interrupt the writer thread
                writerThread.interrupt();
            }
            // to avoid calling close() twice by shutdown if socket was already closed by try-with-resources
            socket = null;
        }
    }

    /**
     * Blocks reading messages from the manager and dispatching by type.
     * Returns normally on disconnect (EOF or socket close); calling code will
     * then attempt reconnection if {@link #running} is still {@code true}.
     */
    private void runMainLoop(InputStream in, BlockingQueue<OutboundMessage> egressQueue, UUID workerId) throws IOException {
        LOG.info("Worker {} entering main loop, waiting for commands...", workerId);

        int consecutiveTimeouts = 0;
        final int MAX_TIMEOUTS = 3;

        while (running) {
            Message message;
            try {
                message = ProtocolDecoder.decode(in);
                consecutiveTimeouts = 0; // reset on successful read
            } catch (SocketTimeoutException e) {
                if (!running) break;
                consecutiveTimeouts++;
                LOG.debug("Worker {}: read timeout {}/{}", workerId, consecutiveTimeouts, MAX_TIMEOUTS);

                if (consecutiveTimeouts >= MAX_TIMEOUTS) {
                    LOG.warn("Worker {}: disconnecting aggressively after {} consecutive read timeouts.", workerId, MAX_TIMEOUTS);
                    break;
                }
                continue;
            } catch (EOFException e) {
                LOG.info("Worker {}: manager closed the connection (stream ended).", workerId);
                return;
            } catch (SocketException e) {
                if (running) {
                    LOG.info("Worker {}: connection reset by manager: {}", workerId, e.getMessage());
                }
                return;
            }

            switch (message.type()) {
                case ASSIGN_JOB -> handleAssignJob(message, egressQueue, workerId);
                default -> LOG.warn("Worker {}: unexpected message type: {}",
                        workerId, message.type());
            }
        }
    }

    /**
     * Handles an {@code ASSIGN_JOB} message end-to-end:
     * <ol>
     *   <li>Sends {@code JOB_RUNNING { jobId }} to the manager.</li>
     *   <li>Executes the task payload via {@link DummyTaskExecutor} with a 60-second timeout.</li>
     *   <li>Sends {@code JOB_RESULT { jobId, status, output }} with COMPLETED or FAILED.</li>
     * </ol>
     * Execution is synchronous on the Virtual-Thread connection thread (no extra thread needed).
     */
    private void handleAssignJob(Message message, BlockingQueue<OutboundMessage> egressQueue, UUID workerId) {
        JsonObject envelope = GSON.fromJson(message.payloadAsString(), JsonObject.class);
        String jobId = envelope.has("jobId") ? envelope.get("jobId").getAsString() : "unknown";
        String payloadJson = envelope.has("payload") ? GSON.toJson(envelope.get("payload")) : "{}";
        long timeoutMs = envelope.has("timeoutMs") ? envelope.get("timeoutMs").getAsLong() : 60000;

        LOG.info("Worker {}: received ASSIGN_JOB — jobId={}, timeoutMs={}", workerId, jobId, timeoutMs);

        // ── 1. Notify manager that we are now executing ───────────────────────
        try {
            JsonObject runningBody = new JsonObject();
            runningBody.addProperty("jobId", jobId);
            byte[] runningBytes = ProtocolEncoder.encode(MessageType.JOB_RUNNING, GSON.toJson(runningBody));
            egressQueue.put(new OutboundMessage(runningBytes,
                    e -> LOG.error("Worker {}: failed to send JOB_RUNNING for job {}", workerId, jobId, e)));
            LOG.info("Worker {}: sent JOB_RUNNING for job {}", workerId, jobId);
        } catch (InterruptedException e) {
            // re-add interrupted flag to current thread
            Thread.currentThread().interrupt();
            LOG.warn("Worker {}: interrupted while sending JOB_RUNNING for job {}", workerId, jobId);
            return;
        }

        // ── 2. Execute the task asynchronously ──────────────────────────────────
        CompletableFuture<Void> future = CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return registry.execute(payloadJson);
                    } catch (UnknownTaskTypeException ex) {
                        // Surface as a clean FAILED result rather than an unexpected error
                        throw new CompletionException(ex);
                    } catch (Exception ex) {
                        throw new CompletionException(ex);
                    }
                }, jobExecutor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .handle((result, ex) -> {
                    String status;
                    String output;
                    if (ex != null) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        status = "FAILED";
                        output = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                        if (cause instanceof UnknownTaskTypeException) {
                            LOG.warn("Worker {}: job {} FAILED — unknown task type: {}", workerId, jobId, cause.getMessage());
                        } else {
                            LOG.warn("Worker {}: job {} FAILED — {}", workerId, jobId, output);
                        }
                    } else {
                        status = "COMPLETED";
                        output = result;
                        LOG.info("Worker {}: job {} COMPLETED — output: {}", workerId, jobId, output);
                    }

                    activeJobs.remove(jobId);

                    // ── 3. Send JOB_RESULT back to manager ───────────────────────────────
                    try {
                        JsonObject resultBody = new JsonObject();
                        resultBody.addProperty("jobId", jobId);
                        resultBody.addProperty("status", status);
                        resultBody.addProperty("output", output);
                        byte[] resultBytes = ProtocolEncoder.encode(MessageType.JOB_RESULT, GSON.toJson(resultBody));
                        egressQueue.put(new OutboundMessage(resultBytes,
                                e -> LOG.error("Worker {}: failed to send JOB_RESULT for job {}", workerId, jobId, e)));
                        LOG.info("Worker {}: sent JOB_RESULT ({}) for job {}", workerId, status, jobId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOG.warn("Worker {}: interrupted while sending JOB_RESULT for job {}", workerId, jobId);
                    }
                    
                    return null;
                });
                
        activeJobs.put(jobId, future);
        // Worker stays in the loop — ready for the next ASSIGN_JOB
    }

    /**
     * Dedicated writer thread loop. Consumes from the queue and writes to the socket.
     * Prevents Virtual Thread pinning caused by using synchronized blocks on the output stream.
     */
    private void runWriterLoop(Socket s, OutputStream out, BlockingQueue<OutboundMessage> queue) {
        LOG.debug("Worker egress writer started");
        try {
            while (running && !s.isClosed()) {
                OutboundMessage msg = queue.take(); // Blocks until a message is available
                try {
                    out.write(msg.payload());
                    out.flush();
                } catch (IOException e) {
                    if (running && !s.isClosed()) {
                        if (msg.onFailure() != null) {
                            msg.onFailure().accept(e);
                        } else {
                            LOG.error("Worker egress writer IO error", e);
                        }
                    }
                    // If a socket IO exception happens, it's generally fatal for this connection,
                    // but we rely on the main read loop or socket closure to actually break this cleanly.
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.debug("Worker egress writer interrupted and stopping");
        }
        LOG.debug("Worker egress writer stopped");
    }

    /**
     * Periodically sends HEARTBEAT messages to the manager.
     */
    private void runHeartbeatLoop(Socket s, BlockingQueue<OutboundMessage> egressQueue, UUID workerId) {
        LOG.info("Worker {}: starting heartbeat loop (interval: {} ms)", workerId, heartbeatIntervalMs);
        byte[] heartbeatBytes = ProtocolEncoder.encode(MessageType.HEARTBEAT, new byte[0]);

        while (running && !s.isClosed()) {
            sleepQuietly(heartbeatIntervalMs);
            if (!running || s.isClosed()) {
                break;
            }
            try {
                egressQueue.put(new OutboundMessage(heartbeatBytes, e -> 
                        LOG.error("Worker {}: failed to send HEARTBEAT", workerId, e)
                ));
                LOG.debug("Worker {}: sent HEARTBEAT", workerId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOG.info("Worker {}: heartbeat loop stopped", workerId);
    }

    /**
     * Bundles a byte payload with an error callback.
     */
    private record OutboundMessage(byte[] payload, Consumer<IOException> onFailure) {}

    /**
     * Signals the client to stop and closes the current socket.
     */
    public void shutdown() {
        LOG.info("WorkerClient shutdown requested.");
        running = false;
        jobExecutor.shutdownNow();
        Socket s = socket;
        if (s != null && !s.isClosed()) {
            try {
                s.close();
            } catch (IOException e) {
                LOG.warn("Error closing socket during shutdown", e);
            }
        }
    }

    // ──── Helpers ────────────────────────────────────────────────────────────

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            LOG.warn("Could not resolve local hostname, using 'unknown'", e);
            return "unknown";
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
