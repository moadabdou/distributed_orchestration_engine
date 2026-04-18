package com.doe.worker.client;

import com.doe.core.executor.JobDefinition;
import com.doe.core.executor.TaskExecutor;
import com.doe.core.protocol.MessageType;
import com.doe.core.protocol.ProtocolDecoder;
import com.doe.core.protocol.ProtocolEncoder;
import com.doe.worker.executor.TaskPluginRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link WorkerClient}.
 * <p>
 * Each test spins up a minimal in-process stub server on a random OS port,
 * exercises a specific scenario, and verifies the client's behaviour.
 * <p>
 * The {@link WorkerClient} runs in a dedicated virtual thread so the tests
 * remain non-blocking and deterministic via {@link CountDownLatch} synchronisation.
 */
class WorkerClientIntegrationTest {

    private static final Gson GSON = new Gson();

    /** Socket timeout for stub server reads (ms). Prevents hangs in CI. */
    private static final int SERVER_TIMEOUT_MS = 5_000;

    private WorkerClient client;
    private Thread clientThread;

    @AfterEach
    void tearDown() throws InterruptedException {
        if (client != null) {
            client.shutdown();
        }
        if (clientThread != null) {
            clientThread.join(3_000);
        }
    }

    // ──── Registration happy path ────────────────────────────────────────────

    @Test
    @DisplayName("Client sends REGISTER_WORKER and receives REGISTER_ACK with a valid UUID")
    void happyPath_registerWorker() throws Exception {
        UUID assignedId = UUID.randomUUID();

        // Latch: set once the client's REGISTER_WORKER frame has been received by the stub
        CountDownLatch registered = new CountDownLatch(1);
        AtomicReference<String> receivedHostname = new AtomicReference<>();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            // ── Stub server ───────────────────────────────────────────────────
            Thread.ofVirtual().start(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(SERVER_TIMEOUT_MS);
                    InputStream in = conn.getInputStream();
                    OutputStream out = conn.getOutputStream();

                    // Read REGISTER_WORKER
                    var msg = ProtocolDecoder.decode(in);
                    assertEquals(MessageType.REGISTER_WORKER, msg.type());

                    JsonObject payload = GSON.fromJson(msg.payloadAsString(), JsonObject.class);
                    receivedHostname.set(payload.get("hostname").getAsString());
                    registered.countDown();

                    // Send REGISTER_ACK
                    JsonObject ack = new JsonObject();
                    ack.addProperty("workerId", assignedId.toString());
                    ack.addProperty("status", "registered");
                    out.write(ProtocolEncoder.encode(MessageType.REGISTER_ACK, GSON.toJson(ack)));
                    out.flush();

                    // Keep connection open until client shuts down
                    Thread.sleep(2_000);
                } catch (Exception ignored) {}
            });

            // ── Start client ──────────────────────────────────────────────────
            client = new WorkerClient("localhost", port, 5000, 10000, "test-token");
            clientThread = Thread.ofVirtual().start(client::start);

            assertTrue(registered.await(5, TimeUnit.SECONDS),
                    "Client should have sent REGISTER_WORKER within 5 seconds");

            // Hostname must be a non-blank string
            assertNotNull(receivedHostname.get());
            assertFalse(receivedHostname.get().isBlank(),
                    "Hostname in REGISTER_WORKER must not be blank");
        }
    }

    // ──── REGISTER_WORKER payload structure ─────────────────────────────────

    @Test
    @DisplayName("REGISTER_WORKER payload contains 'hostname' field and no 'workerId' (manager assigns it)")
    void registerWorker_payloadHasHostnameNoWorkerId() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<JsonObject> capturedPayload = new AtomicReference<>();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            Thread.ofVirtual().start(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(SERVER_TIMEOUT_MS);

                    var msg = ProtocolDecoder.decode(conn.getInputStream());
                    capturedPayload.set(GSON.fromJson(msg.payloadAsString(), JsonObject.class));
                    done.countDown();
                } catch (Exception ignored) {}
            });

            client = new WorkerClient("localhost", port, 5000, 10000, "test-token");
            clientThread = Thread.ofVirtual().start(client::start);

            assertTrue(done.await(5, TimeUnit.SECONDS));

            JsonObject payload = capturedPayload.get();
            assertNotNull(payload, "Payload must not be null");
            assertTrue(payload.has("hostname"),
                    "REGISTER_WORKER must include 'hostname'");
            assertTrue(payload.has("auth_token"),
                    "REGISTER_WORKER must include 'auth_token'");
            assertFalse(payload.has("workerId"),
                    "REGISTER_WORKER must NOT include 'workerId' (manager assigns it)");
        }
    }

    // ──── ACK missing workerId field ─────────────────────────────────────────

    @Test
    @DisplayName("Client exits cleanly when REGISTER_ACK is missing 'workerId'")
    void registerAck_missingWorkerIdCausesCleanExit() throws Exception {
        CountDownLatch clientStopped = new CountDownLatch(1);

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            // Stub: send a malformed ACK without workerId
            Thread.ofVirtual().start(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(SERVER_TIMEOUT_MS);
                    InputStream in = conn.getInputStream();
                    OutputStream out = conn.getOutputStream();

                    ProtocolDecoder.decode(in); // consume REGISTER_WORKER

                    JsonObject badAck = new JsonObject();
                    badAck.addProperty("status", "registered");
                    // deliberately omit "workerId"
                    out.write(ProtocolEncoder.encode(MessageType.REGISTER_ACK, GSON.toJson(badAck)));
                    out.flush();
                } catch (Exception ignored) {}
            });

            client = new WorkerClient("localhost", port, 5000, 10000, "test-token");
            clientThread = Thread.ofVirtual().start(() -> {
                client.start();
                clientStopped.countDown();
            });

            // Trigger shutdown after a moment so the reconnect loop terminates
            Thread.sleep(200);
            client.shutdown();

            assertTrue(clientStopped.await(5, TimeUnit.SECONDS),
                    "Client should have stopped after malformed ACK + shutdown signal");
        }
    }

    // ──── Wrong message type instead of REGISTER_ACK ─────────────────────────

    @Test
    @DisplayName("Client exits cleanly when manager sends wrong type instead of REGISTER_ACK")
    void registerAck_wrongMessageTypeCausesIOException() throws Exception {
        CountDownLatch clientStopped = new CountDownLatch(1);

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            Thread.ofVirtual().start(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(SERVER_TIMEOUT_MS);
                    InputStream in = conn.getInputStream();
                    OutputStream out = conn.getOutputStream();

                    ProtocolDecoder.decode(in); // consume REGISTER_WORKER

                    // Send HEARTBEAT instead of REGISTER_ACK
                    out.write(ProtocolEncoder.encode(MessageType.HEARTBEAT, "{}"));
                    out.flush();
                } catch (Exception ignored) {}
            });

            client = new WorkerClient("localhost", port, 5000, 10000, "test-token");
            clientThread = Thread.ofVirtual().start(() -> {
                client.start();
                clientStopped.countDown();
            });

            Thread.sleep(200);
            client.shutdown();

            assertTrue(clientStopped.await(5, TimeUnit.SECONDS),
                    "Client should have stopped after wrong ACK type + shutdown signal");
        }
    }

    // ──── Graceful disconnect (manager closes connection) ────────────────────

    @Test
    @DisplayName("Client handles graceful manager disconnect (EOF) without error")
    void mainLoop_gracefulDisconnect() throws Exception {
        UUID assignedId = UUID.randomUUID();
        // Fired once the stub has sent the ACK and is about to close
        CountDownLatch ackSent = new CountDownLatch(1);
        // Fired once the client thread exits its start() method
        CountDownLatch clientExited = new CountDownLatch(1);

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            // ── Start client first so it connects once the stub accepts ───────
            client = new WorkerClient("localhost", port, 5000, 10000, "test-token");
            clientThread = Thread.ofVirtual().start(() -> {
                client.start();
                clientExited.countDown();
            });

            // ── Stub: accept one connection, send ACK, then close ─────────────
            Thread.ofVirtual().start(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(SERVER_TIMEOUT_MS);
                    InputStream in = conn.getInputStream();
                    OutputStream out = conn.getOutputStream();

                    ProtocolDecoder.decode(in); // consume REGISTER_WORKER

                    // Send valid ACK
                    JsonObject ack = new JsonObject();
                    ack.addProperty("workerId", assignedId.toString());
                    ack.addProperty("status", "registered");
                    out.write(ProtocolEncoder.encode(MessageType.REGISTER_ACK, GSON.toJson(ack)));
                    out.flush();

                    ackSent.countDown();
                    // try-with-resources closes socket → client sees EOF
                } catch (Exception ignored) {}
            });

            assertTrue(ackSent.await(5, TimeUnit.SECONDS),
                    "Stub should have sent ACK within 5 seconds");

            // After EOF the client will try to reconnect; shut it down
            Thread.sleep(200);
            client.shutdown();

            assertTrue(clientExited.await(5, TimeUnit.SECONDS),
                    "Client thread should have exited after shutdown()");
            clientThread.join(1_000); // give JVM a moment to fully retire the thread
            assertFalse(clientThread.isAlive(), "Client thread should not be alive");
        }
    }


    // ──── ASSIGN_JOB forwarded to main loop ──────────────────────────────────

    @Test
    @DisplayName("Client receives ASSIGN_JOB message without error and continues loop")
    void mainLoop_receivesAssignJob() throws Exception {
        UUID assignedId = UUID.randomUUID();
        CountDownLatch jobReceived = new CountDownLatch(1);

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            Thread.ofVirtual().start(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(SERVER_TIMEOUT_MS);
                    InputStream in = conn.getInputStream();
                    OutputStream out = conn.getOutputStream();

                    ProtocolDecoder.decode(in); // consume REGISTER_WORKER

                    // Send valid ACK
                    JsonObject ack = new JsonObject();
                    ack.addProperty("workerId", assignedId.toString());
                    ack.addProperty("status", "registered");
                    out.write(ProtocolEncoder.encode(MessageType.REGISTER_ACK, GSON.toJson(ack)));
                    out.flush();

                    // Send an ASSIGN_JOB
                    JsonObject job = new JsonObject();
                    job.addProperty("jobId", UUID.randomUUID().toString());
                    JsonObject payload = new JsonObject();
                    payload.addProperty("type", "echo");
                    payload.addProperty("data", "hello");
                    job.add("payload", payload);
                    out.write(ProtocolEncoder.encode(MessageType.ASSIGN_JOB, GSON.toJson(job)));
                    out.flush();
                    jobReceived.countDown();

                    // Keep connection alive so the client stays in the loop
                    Thread.sleep(2_000);
                } catch (Exception ignored) {}
            });

            client = new WorkerClient("localhost", port, 5000, 10000, "test-token");
            clientThread = Thread.ofVirtual().start(client::start);

            assertTrue(jobReceived.await(5, TimeUnit.SECONDS),
                    "ASSIGN_JOB should have been sent to the client within 5 seconds");

            // Give client a moment to process the message, then verify it's still running
            Thread.sleep(200);
            assertTrue(clientThread.isAlive(),
                    "Client should still be running after receiving ASSIGN_JOB");
        }
    }

    // ──── Shutdown ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("shutdown() causes client to stop even while blocked in main loop")
    void shutdown_stopsBlockingClient() throws Exception {
        UUID assignedId = UUID.randomUUID();
        CountDownLatch inLoop = new CountDownLatch(1);

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            Thread.ofVirtual().start(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(SERVER_TIMEOUT_MS);
                    InputStream in = conn.getInputStream();
                    OutputStream out = conn.getOutputStream();

                    ProtocolDecoder.decode(in); // consume REGISTER_WORKER

                    JsonObject ack = new JsonObject();
                    ack.addProperty("workerId", assignedId.toString());
                    ack.addProperty("status", "registered");
                    out.write(ProtocolEncoder.encode(MessageType.REGISTER_ACK, GSON.toJson(ack)));
                    out.flush();

                    inLoop.countDown();
                    Thread.sleep(10_000); // Hold connection open
                } catch (Exception ignored) {}
            });

            client = new WorkerClient("localhost", port, 5000, 10000, "test-token");
            clientThread = Thread.ofVirtual().start(client::start);

            assertTrue(inLoop.await(5, TimeUnit.SECONDS),
                    "Client should have reached the main loop");

            Thread.sleep(100); // ensure client is blocked in decode()
            client.shutdown();
            clientThread.join(3_000);

            assertFalse(clientThread.isAlive(),
                    "Client thread should have exited after shutdown()");
        }
    }
    // ──── Timeout & Reconnect ────────────────────────────────────────────────
    
    @Test
    @DisplayName("Client disconnects aggressively after 3 consecutive read timeouts")
    void mainLoop_disconnectsOnConsecutiveTimeouts() throws Exception {
        CountDownLatch reconnected = new CountDownLatch(1);

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            
            Thread.ofVirtual().start(() -> {
                try {
                    // First connection
                    try (Socket conn1 = serverSocket.accept()) {
                        conn1.setSoTimeout(SERVER_TIMEOUT_MS);
                        ProtocolDecoder.decode(conn1.getInputStream()); // consume REGISTER_WORKER
                        
                        JsonObject ack = new JsonObject();
                        ack.addProperty("workerId", UUID.randomUUID().toString());
                        ack.addProperty("status", "registered");
                        OutputStream out = conn1.getOutputStream();
                        out.write(ProtocolEncoder.encode(MessageType.REGISTER_ACK, GSON.toJson(ack)));
                        out.flush();
                        
                        // We do NOT send any further messages to simulate a dead connection
                        // The client will hit its readTimeoutMs = 100ms three times in ~300ms, then drop us.
                        // We keep trying to read until we hit EOF (meaning client closed).
                        try {
                            while (conn1.getInputStream().read() != -1) { }
                        } catch (Exception expected) { }
                    }
                    
                    // Second connection: validates that the client disconnected from the first and retried
                    try (Socket conn2 = serverSocket.accept()) {
                        conn2.setSoTimeout(SERVER_TIMEOUT_MS);
                        var msg = ProtocolDecoder.decode(conn2.getInputStream());
                        if (msg.type() == MessageType.REGISTER_WORKER) {
                            reconnected.countDown();
                        }
                    }
                } catch (Exception ignored) {}
            });

            // Set very short readTimeoutMs for the test (100ms)
            client = new WorkerClient("localhost", port, 5000, 100, "test-token");
            clientThread = Thread.ofVirtual().start(client::start);

            assertTrue(reconnected.await(5, TimeUnit.SECONDS),
                    "Client should have aggressively disconnected and reconnected after consecutive timeouts");
        }
    }

    // ──── Bash plugin integration ────────────────────────────────────────────

    @Test
    @DisplayName("ASSIGN_JOB with 'bash' type returns COMPLETED JOB_RESULT with script output")
    void mainLoop_bashJob_completedWithOutput() throws Exception {
        UUID assignedId = UUID.randomUUID();
        CountDownLatch resultReceived = new CountDownLatch(1);
        AtomicReference<JsonObject> capturedResult = new AtomicReference<>();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            Thread.ofVirtual().start(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(SERVER_TIMEOUT_MS);
                    InputStream in = conn.getInputStream();
                    OutputStream out = conn.getOutputStream();

                    ProtocolDecoder.decode(in); // consume REGISTER_WORKER

                    // Send valid ACK
                    JsonObject ack = new JsonObject();
                    ack.addProperty("workerId", assignedId.toString());
                    ack.addProperty("status", "registered");
                    out.write(ProtocolEncoder.encode(MessageType.REGISTER_ACK, GSON.toJson(ack)));
                    out.flush();

                    // Send ASSIGN_JOB with a bash payload
                    JsonObject jobPayload = new JsonObject();
                    jobPayload.addProperty("type", "bash");
                    jobPayload.addProperty("script", "echo integration-ok");
                    jobPayload.addProperty("timeoutMs", 5000);

                    JsonObject job = new JsonObject();
                    job.addProperty("jobId", UUID.randomUUID().toString());
                    job.add("payload", jobPayload);
                    job.addProperty("timeoutMs", 10000);

                    out.write(ProtocolEncoder.encode(MessageType.ASSIGN_JOB, GSON.toJson(job)));
                    out.flush();

                    // Read messages until we get JOB_RESULT (skip JOB_RUNNING / HEARTBEATs)
                    while (true) {
                        var msg = ProtocolDecoder.decode(in);
                        if (msg.type() == MessageType.JOB_RESULT) {
                            capturedResult.set(GSON.fromJson(msg.payloadAsString(), JsonObject.class));
                            resultReceived.countDown();
                            break;
                        }
                    }

                    Thread.sleep(1_000); // keep connection open while client processes
                } catch (Exception ignored) {}
            });

            client = new WorkerClient("localhost", port, 5000, 10000, "test-token");
            clientThread = Thread.ofVirtual().start(client::start);

            assertTrue(resultReceived.await(10, TimeUnit.SECONDS),
                    "JOB_RESULT should be received within 10 seconds");

            JsonObject result = capturedResult.get();
            assertNotNull(result, "JOB_RESULT payload must not be null");
            assertEquals("COMPLETED", result.get("status").getAsString(),
                    "Job status should be COMPLETED");
            assertTrue(result.get("output").getAsString().contains("integration-ok"),
                    "Output should contain 'integration-ok', got: " + result.get("output"));
        }
    }

    @Test
    @DisplayName("ASSIGN_JOB with unknown task type returns FAILED JOB_RESULT")
    void mainLoop_unknownTaskType_failedResult() throws Exception {
        UUID assignedId = UUID.randomUUID();
        CountDownLatch resultReceived = new CountDownLatch(1);
        AtomicReference<JsonObject> capturedResult = new AtomicReference<>();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            Thread.ofVirtual().start(() -> {
                try (Socket conn = serverSocket.accept()) {
                    conn.setSoTimeout(SERVER_TIMEOUT_MS);
                    InputStream in = conn.getInputStream();
                    OutputStream out = conn.getOutputStream();

                    ProtocolDecoder.decode(in); // consume REGISTER_WORKER

                    JsonObject ack = new JsonObject();
                    ack.addProperty("workerId", assignedId.toString());
                    ack.addProperty("status", "registered");
                    out.write(ProtocolEncoder.encode(MessageType.REGISTER_ACK, GSON.toJson(ack)));
                    out.flush();

                    JsonObject jobPayload = new JsonObject();
                    jobPayload.addProperty("type", "this-type-does-not-exist");

                    JsonObject job = new JsonObject();
                    job.addProperty("jobId", UUID.randomUUID().toString());
                    job.add("payload", jobPayload);
                    job.addProperty("timeoutMs", 5000);

                    out.write(ProtocolEncoder.encode(MessageType.ASSIGN_JOB, GSON.toJson(job)));
                    out.flush();

                    while (true) {
                        var msg = ProtocolDecoder.decode(in);
                        if (msg.type() == MessageType.JOB_RESULT) {
                            capturedResult.set(GSON.fromJson(msg.payloadAsString(), JsonObject.class));
                            resultReceived.countDown();
                            break;
                        }
                    }

                    Thread.sleep(1_000);
                } catch (Exception ignored) {}
            });

            client = new WorkerClient("localhost", port, 5000, 10000, "test-token");
            clientThread = Thread.ofVirtual().start(client::start);

            assertTrue(resultReceived.await(10, TimeUnit.SECONDS),
                    "JOB_RESULT should be received within 10 seconds");

            JsonObject result = capturedResult.get();
            assertNotNull(result);
            assertEquals("FAILED", result.get("status").getAsString(),
                    "Job status should be FAILED for unknown type");
            assertTrue(result.get("output").getAsString().contains("this-type-does-not-exist"),
                    "Error output should mention the unknown type");
        }
    }

    @Test
    @DisplayName("Manual cancellation propagates cancel() to executor and sends CANCELLED result")
    void mainLoop_manualCancel_invokesPluginCancel() throws Exception {
        UUID assignedId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        CountDownLatch resultReceived = new CountDownLatch(1);
        AtomicReference<JsonObject> capturedResult = new AtomicReference<>();
        
        // Use a mock executor that waits until cancelled
        CountDownLatch cancelCalled = new CountDownLatch(1);
        TaskExecutor mockExecutor = new TaskExecutor() {
            @Override public String getType() { return "mock-cancel"; }
            @Override public void cancel() { cancelCalled.countDown(); }
            @Override public void validate(JobDefinition d) {}
            @Override public String execute(JobDefinition d, com.doe.core.executor.ExecutionContext c) throws Exception {
                Thread.sleep(10000); // Block until interrupted or cancelled
                return "done";
            }
        };

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            Thread.ofVirtual().start(() -> {
                try (Socket conn = serverSocket.accept()) {
                    InputStream in = conn.getInputStream();
                    OutputStream out = conn.getOutputStream();
                    ProtocolDecoder.decode(in); // REGISTER_WORKER
                    
                    JsonObject ack = new JsonObject();
                    ack.addProperty("workerId", assignedId.toString());
                    ack.addProperty("status", "registered");
                    out.write(ProtocolEncoder.encode(MessageType.REGISTER_ACK, GSON.toJson(ack)));
                    out.flush();

                    // 1. Assign Job
                    JsonObject job = new JsonObject();
                    job.addProperty("jobId", jobId.toString());
                    JsonObject p = new JsonObject();
                    p.addProperty("type", "mock-cancel");
                    job.add("payload", p);
                    out.write(ProtocolEncoder.encode(MessageType.ASSIGN_JOB, GSON.toJson(job)));
                    out.flush();

                    // Wait for it to be running (simplification: just wait a bit)
                    Thread.sleep(200);

                    // 2. Cancel Job
                    JsonObject cancel = new JsonObject();
                    cancel.addProperty("jobId", jobId.toString());
                    out.write(ProtocolEncoder.encode(MessageType.CANCEL_JOB, GSON.toJson(cancel)));
                    out.flush();

                    // 3. Wait for result
                    while (true) {
                        var msg = ProtocolDecoder.decode(in);
                        if (msg.type() == MessageType.JOB_RESULT) {
                            capturedResult.set(GSON.fromJson(msg.payloadAsString(), JsonObject.class));
                            resultReceived.countDown();
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            });

            client = new WorkerClient("localhost", port, 5000, 10000, "test-token", 
                    new TaskPluginRegistry().register("mock-cancel", mockExecutor));
            clientThread = Thread.ofVirtual().start(client::start);

            assertTrue(cancelCalled.await(5, TimeUnit.SECONDS), "Executor.cancel() should be called");
            assertTrue(resultReceived.await(5, TimeUnit.SECONDS), "JOB_RESULT should be received");
            
            assertEquals("CANCELLED", capturedResult.get().get("status").getAsString());
        }
    }

    @Test
    @DisplayName("Timeout propagates cancel() to executor and sends FAILED result")
    void mainLoop_timeout_invokesPluginCancel() throws Exception {
        UUID assignedId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        CountDownLatch resultReceived = new CountDownLatch(1);
        AtomicReference<JsonObject> capturedResult = new AtomicReference<>();
        CountDownLatch cancelCalled = new CountDownLatch(1);

        TaskExecutor mockExecutor = new TaskExecutor() {
            @Override public String getType() { return "mock-timeout"; }
            @Override public void cancel() { cancelCalled.countDown(); }
            @Override public void validate(JobDefinition d) {}
            @Override public String execute(JobDefinition d, com.doe.core.executor.ExecutionContext c) throws Exception {
                Thread.sleep(10000);
                return "too-late";
            }
        };

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            Thread.ofVirtual().start(() -> {
                try (Socket conn = serverSocket.accept()) {
                    InputStream in = conn.getInputStream();
                    OutputStream out = conn.getOutputStream();
                    ProtocolDecoder.decode(in); // REGISTER_WORKER
                    out.write(ProtocolEncoder.encode(MessageType.REGISTER_ACK, "{\"workerId\":\"" + assignedId + "\"}"));
                    out.flush();

                    JsonObject job = new JsonObject();
                    job.addProperty("jobId", jobId.toString());
                    job.addProperty("timeoutMs", 200); // Short timeout
                    JsonObject p = new JsonObject();
                    p.addProperty("type", "mock-timeout");
                    job.add("payload", p);
                    out.write(ProtocolEncoder.encode(MessageType.ASSIGN_JOB, GSON.toJson(job)));
                    out.flush();

                    while (true) {
                        var msg = ProtocolDecoder.decode(in);
                        if (msg.type() == MessageType.JOB_RESULT) {
                            capturedResult.set(GSON.fromJson(msg.payloadAsString(), JsonObject.class));
                            resultReceived.countDown();
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            });

            client = new WorkerClient("localhost", port, 5000, 10000, "test-token",
                    new TaskPluginRegistry().register("mock-timeout", mockExecutor));
            clientThread = Thread.ofVirtual().start(client::start);

            assertTrue(cancelCalled.await(5, TimeUnit.SECONDS), "Executor.cancel() should be called on timeout");
            assertTrue(resultReceived.await(5, TimeUnit.SECONDS));
            
            assertEquals("FAILED", capturedResult.get().get("status").getAsString());
            assertTrue(capturedResult.get().get("output").getAsString().contains("timed out"));
        }
    }

    @Test
    @DisplayName("Cancelling one job does not affect other concurrent jobs of the same type")
    void mainLoop_cancelIsolation() throws Exception {
        UUID assignedId = UUID.randomUUID();
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        
        CountDownLatch cancel1Called = new CountDownLatch(1);
        CountDownLatch cancel2Called = new CountDownLatch(1);
        CountDownLatch job2Done = new CountDownLatch(1);

        // Registry that returns fresh instances via SPI-like providers
        TaskPluginRegistry registry = new TaskPluginRegistry() {
            private int count = 0;
            @Override
            public java.util.Optional<TaskExecutor> getExecutor(String type) {
                if (!"isolated".equals(type)) return java.util.Optional.empty();
                int id = ++count;
                return java.util.Optional.of(new TaskExecutor() {
                    @Override public String getType() { return "isolated"; }
                    @Override public void cancel() { 
                        if (id == 1) cancel1Called.countDown(); 
                        if (id == 2) cancel2Called.countDown();
                    }
                    @Override public void validate(JobDefinition d) {}
                    @Override public String execute(JobDefinition d, com.doe.core.executor.ExecutionContext c) throws Exception {
                        if (id == 1) {
                            Thread.sleep(10000); // Wait to be cancelled
                        } else {
                            Thread.sleep(500); // Complete normally
                            job2Done.countDown();
                        }
                        return "success-" + id;
                    }
                });
            }
        };

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            Thread.ofVirtual().start(() -> {
                try (Socket conn = serverSocket.accept()) {
                    InputStream in = conn.getInputStream();
                    OutputStream out = conn.getOutputStream();
                    ProtocolDecoder.decode(in); // REGISTER_WORKER
                    out.write(ProtocolEncoder.encode(MessageType.REGISTER_ACK, "{\"workerId\":\"" + assignedId + "\"}"));
                    out.flush();

                    // 1. Assign Job 1
                    JsonObject job1 = new JsonObject();
                    job1.addProperty("jobId", jobId1.toString());
                    job1.add("payload", GSON.fromJson("{\"type\":\"isolated\"}", JsonObject.class));
                    out.write(ProtocolEncoder.encode(MessageType.ASSIGN_JOB, GSON.toJson(job1)));
                    out.flush();

                    // 2. Assign Job 2
                    JsonObject job2 = new JsonObject();
                    job2.addProperty("jobId", jobId2.toString());
                    job2.add("payload", GSON.fromJson("{\"type\":\"isolated\"}", JsonObject.class));
                    out.write(ProtocolEncoder.encode(MessageType.ASSIGN_JOB, GSON.toJson(job2)));
                    out.flush();

                    Thread.sleep(100);

                    // 3. Cancel Job 1
                    JsonObject cancel1 = new JsonObject();
                    cancel1.addProperty("jobId", jobId1.toString());
                    out.write(ProtocolEncoder.encode(MessageType.CANCEL_JOB, GSON.toJson(cancel1)));
                    out.flush();
                    
                    // Keep connection open
                    Thread.sleep(1000);
                } catch (Exception ignored) {}
            });

            client = new WorkerClient("localhost", port, 5000, 10000, "test-token", registry);
            clientThread = Thread.ofVirtual().start(client::start);

            assertTrue(cancel1Called.await(5, TimeUnit.SECONDS), "Job 1 should be cancelled");
            assertTrue(job2Done.await(5, TimeUnit.SECONDS), "Job 2 should complete normally");
            assertEquals(1, cancel2Called.getCount(), "Job 2 should NOT be cancelled");
        }
    }
}

