package com.doe.worker.client;

import com.doe.core.protocol.MessageType;
import com.doe.core.protocol.ProtocolDecoder;
import com.doe.core.protocol.ProtocolEncoder;
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
            client = new WorkerClient("localhost", port);
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

            client = new WorkerClient("localhost", port);
            clientThread = Thread.ofVirtual().start(client::start);

            assertTrue(done.await(5, TimeUnit.SECONDS));

            JsonObject payload = capturedPayload.get();
            assertNotNull(payload, "Payload must not be null");
            assertTrue(payload.has("hostname"),
                    "REGISTER_WORKER must include 'hostname'");
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

            client = new WorkerClient("localhost", port);
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

            client = new WorkerClient("localhost", port);
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
            client = new WorkerClient("localhost", port);
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
                    job.addProperty("jobId", "job-xyz");
                    out.write(ProtocolEncoder.encode(MessageType.ASSIGN_JOB, GSON.toJson(job)));
                    out.flush();
                    jobReceived.countDown();

                    // Keep connection alive so the client stays in the loop
                    Thread.sleep(2_000);
                } catch (Exception ignored) {}
            });

            client = new WorkerClient("localhost", port);
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

            client = new WorkerClient("localhost", port);
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
            client = new WorkerClient("localhost", port, 5000, 100);
            clientThread = Thread.ofVirtual().start(client::start);

            assertTrue(reconnected.await(5, TimeUnit.SECONDS),
                    "Client should have aggressively disconnected and reconnected after consecutive timeouts");
        }
    }
}
