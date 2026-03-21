package com.doe.manager.server;

import com.doe.core.protocol.Message;
import com.doe.core.protocol.MessageType;
import com.doe.core.protocol.ProtocolDecoder;
import com.doe.core.protocol.ProtocolEncoder;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link ManagerServer}.
 * <p>
 * Starts a real server on a random port, connects raw sockets,
 * and verifies registration, heartbeat, and registry management.
 */
class ManagerServerIntegrationTest {

    private ManagerServer server;
    private Thread serverThread;

    @BeforeEach
    void startServer() throws Exception {
        // Use port 0 to let the OS assign an available port
        server = new ManagerServer(0);

        CountDownLatch serverReady = new CountDownLatch(1);

        serverThread = Thread.ofVirtual().start(() -> {
            try {
                // Signal readiness right before blocking on start()
                serverReady.countDown();
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for the server thread to be scheduled
        assertTrue(serverReady.await(5, TimeUnit.SECONDS), "Server failed to start");
        // Small delay to ensure ServerSocket.accept() is running
        Thread.sleep(200);
    }

    @AfterEach
    void stopServer() throws Exception {
        server.shutdown();
        serverThread.join(5000);
    }

    @Test
    @DisplayName("3 workers register and appear in the registry")
    void threeWorkersRegister() throws Exception {
        List<Socket> clients = new ArrayList<>();
        List<UUID> workerIds = new ArrayList<>();

        try {
            for (int i = 0; i < 3; i++) {
                UUID workerId = UUID.randomUUID();
                workerIds.add(workerId);

                Socket socket = new Socket("localhost", server.getLocalPort());
                clients.add(socket);

                sendRegistration(socket, workerId);
                Message ack = readAck(socket);

                assertEquals(MessageType.REGISTER_ACK, ack.type(),
                        "Expected REGISTER_ACK response");

                String ackPayload = ack.payloadAsString();
                assertTrue(ackPayload.contains(workerId.toString()),
                        "ACK should echo the worker ID");
            }

            // Allow time for server to process all registrations
            Thread.sleep(300);

            assertEquals(3, server.getRegistry().size(),
                    "Registry should contain exactly 3 workers");

            // Verify each worker is individually findable
            for (UUID id : workerIds) {
                assertTrue(server.getRegistry().get(id).isPresent(),
                        "Worker " + id + " should be in the registry");
            }
        } finally {
            clients.forEach(s -> {
                try { s.close(); } catch (IOException ignored) {}
            });
        }
    }

    @Test
    @DisplayName("Heartbeat updates lastHeartbeat timestamp")
    void heartbeatUpdatesTimestamp() throws Exception {
        UUID workerId = UUID.randomUUID();

        try (Socket socket = new Socket("localhost", server.getLocalPort())) {
            sendRegistration(socket, workerId);
            readAck(socket);

            Thread.sleep(200);

            Instant beforeHeartbeat = server.getRegistry().get(workerId)
                    .orElseThrow().getLastHeartbeat();

            // Small delay so the new timestamp is definitely different
            Thread.sleep(50);

            sendHeartbeat(socket, workerId);

            // Allow server to process
            Thread.sleep(200);

            Instant afterHeartbeat = server.getRegistry().get(workerId)
                    .orElseThrow().getLastHeartbeat();

            assertTrue(afterHeartbeat.isAfter(beforeHeartbeat),
                    "Heartbeat should update lastHeartbeat timestamp");
        }
    }

    @Test
    @DisplayName("Server accepts registration and responds with ACK")
    void registrationAck() throws Exception {
        UUID workerId = UUID.randomUUID();

        try (Socket socket = new Socket("localhost", server.getLocalPort())) {
            sendRegistration(socket, workerId);
            Message ack = readAck(socket);

            assertNotNull(ack, "Should receive an ACK message");
            assertEquals(MessageType.REGISTER_ACK, ack.type());

            String payload = ack.payloadAsString();
            assertTrue(payload.contains("registered"), "ACK should contain 'registered' status");
        }
    }

    // ──── Helpers ───────────────────────────────────────────────────

    private void sendRegistration(Socket socket, UUID workerId) throws IOException {
        String json = """
                {"workerId":"%s","hostname":"test-node"}""".formatted(workerId);

        byte[] wire = ProtocolEncoder.encode(MessageType.REGISTER_WORKER,
                json.getBytes(StandardCharsets.UTF_8));

        OutputStream out = socket.getOutputStream();
        out.write(wire);
        out.flush();
    }

    private void sendHeartbeat(Socket socket, UUID workerId) throws IOException {
        String json = """
                {"workerId":"%s","timestamp":%d}""".formatted(workerId, System.currentTimeMillis());

        byte[] wire = ProtocolEncoder.encode(MessageType.HEARTBEAT,
                json.getBytes(StandardCharsets.UTF_8));

        OutputStream out = socket.getOutputStream();
        out.write(wire);
        out.flush();
    }

    private Message readAck(Socket socket) throws IOException {
        socket.setSoTimeout(5000); // 5s read timeout
        InputStream in = socket.getInputStream();
        return ProtocolDecoder.decode(in);
    }
}
