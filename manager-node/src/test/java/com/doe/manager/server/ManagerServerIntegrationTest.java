package com.doe.manager.server;

import com.doe.core.protocol.Message;
import com.doe.core.protocol.MessageType;
import com.doe.core.protocol.ProtocolDecoder;
import com.doe.core.protocol.ProtocolEncoder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

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
 * <p>
 * Workers do <b>not</b> supply their own UUID — the manager assigns one
 * in the {@code REGISTER_ACK} response.
 */
class ManagerServerIntegrationTest {

    private static final Gson GSON = new Gson();

    private ManagerServer server;
    private Thread serverThread;

    @BeforeEach
    void startServer() throws Exception {
        server = TestManagerServerBuilder.build(0);

        CountDownLatch serverReady = new CountDownLatch(1);

        serverThread = Thread.ofVirtual().start(() -> {
            serverReady.countDown();
            server.start();
        });

        assertTrue(serverReady.await(5, TimeUnit.SECONDS), "Server failed to start");
        Thread.sleep(200);
    }

    @AfterEach
    void stopServer() throws Exception {
        server.shutdown();
        serverThread.join(5000);
    }

    @Test
    @DisplayName("3 workers register and appear in the registry with manager-assigned UUIDs")
    void threeWorkersRegister() throws Exception {
        List<Socket> clients = new ArrayList<>();
        List<UUID> assignedIds = new ArrayList<>();

        try {
            for (int i = 0; i < 3; i++) {
                Socket socket = new Socket("localhost", server.getLocalPort());
                clients.add(socket);

                UUID presetId = UUID.randomUUID();
                String token = generateToken(presetId);
                sendRegistration(socket, "test-node-" + i, token);
                UUID assignedId = readAssignedId(socket);
                assertEquals(presetId, assignedId, "Worker ID should match the JWT subject");
                assignedIds.add(assignedId);
            }

            Thread.sleep(300);

            assertEquals(3, server.getRegistry().size(),
                    "Registry should contain exactly 3 workers");

            for (UUID id : assignedIds) {
                assertTrue(server.getRegistry().get(id).isPresent(),
                        "Worker " + id + " should be in the registry");
            }

            // Verify all assigned UUIDs are unique
            assertEquals(3, assignedIds.stream().distinct().count(),
                    "All assigned UUIDs should be unique");
        } finally {
            clients.forEach(s -> {
                try { s.close(); } catch (IOException ignored) {}
            });
        }
    }

    @Test
    @DisplayName("Heartbeat updates lastHeartbeat timestamp")
    void heartbeatUpdatesTimestamp() throws Exception {
        try (Socket socket = new Socket("localhost", server.getLocalPort())) {
            UUID presetId = UUID.randomUUID();
            sendRegistration(socket, "heartbeat-test-node", generateToken(presetId));
            UUID assignedId = readAssignedId(socket);

            Thread.sleep(200);

            Instant beforeHeartbeat = server.getRegistry().get(assignedId)
                    .orElseThrow().getLastHeartbeat();

            Thread.sleep(50);

            sendHeartbeat(socket);

            Thread.sleep(200);

            Instant afterHeartbeat = server.getRegistry().get(assignedId)
                    .orElseThrow().getLastHeartbeat();

            assertTrue(afterHeartbeat.isAfter(beforeHeartbeat),
                    "Heartbeat should update lastHeartbeat timestamp");
        }
    }

    @Test
    @DisplayName("Server assigns UUID and responds with ACK")
    void registrationAck() throws Exception {
        try (Socket socket = new Socket("localhost", server.getLocalPort())) {
            UUID presetId = UUID.randomUUID();
            sendRegistration(socket, "ack-test-node", generateToken(presetId));
            Message ack = readAck(socket);

            assertNotNull(ack, "Should receive an ACK message");
            assertEquals(MessageType.REGISTER_ACK, ack.type());

            JsonObject json = GSON.fromJson(ack.payloadAsString(), JsonObject.class);
            assertTrue(json.has("workerId"), "ACK should contain a workerId");
            assertEquals("registered", json.get("status").getAsString(),
                    "ACK should contain 'registered' status");

            // Verify the assigned UUID is valid
            assertDoesNotThrow(() -> UUID.fromString(json.get("workerId").getAsString()),
                    "Assigned workerId should be a valid UUID");
        }
    }

    @Test
    @DisplayName("HeartbeatMonitor evicts worker that stops sending heartbeats")
    void deadWorkerIsDetectedAndRemoved() throws Exception {
        // Start a custom server with short heartbeat intervals (100ms check, 300ms timeout)
        ManagerServer customServer = TestManagerServerBuilder.build(0, 100, 300);
        CountDownLatch serverReady = new CountDownLatch(1);
        Thread customServerThread = Thread.ofVirtual().start(() -> {
            serverReady.countDown();
            customServer.start();
        });
        assertTrue(serverReady.await(5, TimeUnit.SECONDS), "Custom server failed to start");
        Thread.sleep(100);

        try {
            Socket socket = new Socket("localhost", customServer.getLocalPort());
            UUID presetId = UUID.randomUUID();
            sendRegistration(socket, "dead-test-node", generateToken(presetId));
            UUID assignedId = readAssignedId(socket);

            assertTrue(customServer.getRegistry().get(assignedId).isPresent(),
                    "Worker should be registered initially");

            // Sleep past the timeout (300ms + 200ms buffer) without sending heartbeats
            // The socket remains open, simulating a frozen worker or network partition
            Thread.sleep(500);

            // The HeartbeatMonitor should have evicted the worker and closed the specific socket
            assertTrue(customServer.getRegistry().isEmpty(),
                    "Registry should be empty after heartbeat timeout");
            
            socket.close();
        } finally {
            customServer.shutdown();
            customServerThread.join(2000);
        }
    }

    // ──── Helpers ───────────────────────────────────────────────────

    private String generateToken(UUID workerId) {
        String secret = "3c34e62a26514757c2c159851f50a80d46dddc7fa0a06df5c689f928e4e9b94z";
        return Jwts.builder()
                .subject(workerId.toString())
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private void sendRegistration(Socket socket, String hostname, String token) throws IOException {
        String json = """
                {"hostname":"%s", "auth_token":"%s"}""".formatted(hostname, token);

        byte[] wire = ProtocolEncoder.encode(MessageType.REGISTER_WORKER,
                json.getBytes(StandardCharsets.UTF_8));

        OutputStream out = socket.getOutputStream();
        out.write(wire);
        out.flush();
    }

    private void sendHeartbeat(Socket socket) throws IOException {
        String json = """
                {"timestamp":%d}""".formatted(System.currentTimeMillis());

        byte[] wire = ProtocolEncoder.encode(MessageType.HEARTBEAT,
                json.getBytes(StandardCharsets.UTF_8));

        OutputStream out = socket.getOutputStream();
        out.write(wire);
        out.flush();
    }

    private Message readAck(Socket socket) throws IOException {
        socket.setSoTimeout(5000);
        InputStream in = socket.getInputStream();
        return ProtocolDecoder.decode(in);
    }

    /**
     * Reads the REGISTER_ACK and extracts the manager-assigned UUID.
     */
    private UUID readAssignedId(Socket socket) throws IOException {
        Message ack = readAck(socket);
        assertEquals(MessageType.REGISTER_ACK, ack.type());
        JsonObject json = GSON.fromJson(ack.payloadAsString(), JsonObject.class);
        return UUID.fromString(json.get("workerId").getAsString());
    }
}
