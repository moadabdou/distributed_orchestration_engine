package com.doe.manager.server;

import com.doe.core.model.WorkerConnection;
import com.doe.core.protocol.Message;
import com.doe.core.protocol.MessageType;
import com.doe.core.protocol.ProtocolDecoder;
import com.doe.core.protocol.ProtocolEncoder;
import com.doe.core.registry.WorkerRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.UUID;

/**
 * Central manager server that accepts worker TCP connections using Java 21 Virtual Threads.
 * <p>
 * Each accepted connection is handled in a dedicated Virtual Thread. The server
 * maintains a thread-safe {@link WorkerRegistry} to track connected workers.
 * <p>
 * Wire protocol: {@code [1B Type][4B Length][NB Payload]}
 */
public class ManagerServer {

    private static final Logger LOG = LoggerFactory.getLogger(ManagerServer.class);
    private static final Gson GSON = new Gson();

    private final int port;
    private final WorkerRegistry registry;

    private volatile boolean running;
    private ServerSocket serverSocket;

    /**
     * Creates a new ManagerServer.
     *
     * @param port the TCP port to bind to
     */
    public ManagerServer(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535, got: " + port);
        }
        this.port = port;
        this.registry = new WorkerRegistry();
    }

    /**
     * Starts the server, binding to the configured port and entering the accept loop.
     * <p>
     * This method blocks until {@link #shutdown()} is called or an error occurs.
     *
     * @throws IOException if the server socket cannot be bound
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        running = true;

        LOG.info("ManagerServer started on port {}", serverSocket.getLocalPort());

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
            }
        }
    }

    /**
     * Handles a single worker connection: reads messages in a loop and dispatches by type.
     */
    private void handleWorker(Socket socket) {
        UUID workerId = null;
        try (socket) {
            var inputStream = socket.getInputStream();

            while (running && !socket.isClosed()) {
                Message message = ProtocolDecoder.decode(inputStream);

                switch (message.type()) {
                    case REGISTER_WORKER -> {
                        workerId = handleRegistration(message, socket);
                        MDC.put("workerId", workerId.toString());
                    }
                    case HEARTBEAT -> {
                        if (workerId != null) {
                            handleHeartbeat(workerId);
                        } else {
                            LOG.warn("Received HEARTBEAT from unregistered connection {}", 
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
            if (workerId != null) {
                registry.unregister(workerId);
                LOG.info("Worker {} removed from registry", workerId);
            }
            MDC.clear();
        }
    }

    /**
     * Processes a REGISTER_WORKER message: parses the JSON payload, creates a
     * {@link WorkerConnection}, registers it, and sends back a REGISTER_ACK.
     *
     * @return the UUID of the registered worker
     */
    private UUID handleRegistration(Message message, Socket socket) throws IOException {
        JsonObject json = GSON.fromJson(message.payloadAsString(), JsonObject.class);

        UUID workerId;
        if (json.has("workerId")) {
            workerId = UUID.fromString(json.get("workerId").getAsString());
        } else {
            workerId = UUID.randomUUID();
        }

        WorkerConnection connection = new WorkerConnection(workerId, socket);
        WorkerConnection previous = registry.register(connection);
        if (previous != null) {
            LOG.warn("Worker {} re-registered, replacing previous connection", workerId);
        }

        LOG.info("Worker {} connected from {}", workerId, connection.getRemoteAddress());

        // Send REGISTER_ACK back to the worker
        JsonObject ackPayload = new JsonObject();
        ackPayload.addProperty("workerId", workerId.toString());
        ackPayload.addProperty("status", "registered");

        byte[] ackBytes = ProtocolEncoder.encode(MessageType.REGISTER_ACK, GSON.toJson(ackPayload));
        OutputStream out = socket.getOutputStream();
        out.write(ackBytes);
        out.flush();

        return workerId;
    }

    /**
     * Processes a HEARTBEAT message: updates the worker's last-seen timestamp.
     */
    private void handleHeartbeat(UUID workerId) {
        registry.get(workerId).ifPresentOrElse(
                connection -> {
                    connection.updateHeartbeat();
                    LOG.debug("Heartbeat received from Worker {}", workerId);
                },
                () -> LOG.warn("Heartbeat from unknown Worker {}", workerId)
        );
    }

    /**
     * Gracefully shuts down the server: closes the server socket and all worker connections.
     */
    public void shutdown() {
        LOG.info("Shutting down ManagerServer...");
        running = false;

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
     * Returns the worker registry (primarily for testing).
     */
    public WorkerRegistry getRegistry() {
        return registry;
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
