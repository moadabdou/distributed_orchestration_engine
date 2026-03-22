package com.doe.core.model;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a connected worker in the distributed orchestration engine.
 * <p>
 * Each instance tracks the worker's unique ID, its TCP socket, and the
 * timestamp of the last received heartbeat. The heartbeat timestamp is
 * updated atomically for lock-free concurrent access.
 */
public class WorkerConnection {

    private final UUID id;
    private final Socket socket;
    private final Instant connectedAt;
    private final AtomicReference<Instant> lastHeartbeat;

    /**
     * Creates a new worker connection.
     *
     * @param id     unique identifier for this worker
     * @param socket the TCP socket connected to the worker
     */
    public WorkerConnection(UUID id, Socket socket) {
        if (id == null) {
            throw new IllegalArgumentException("Worker ID must not be null");
        }
        if (socket == null) {
            throw new IllegalArgumentException("Socket must not be null");
        }
        this.id = id;
        this.socket = socket;
        this.connectedAt = Instant.now();
        this.lastHeartbeat = new AtomicReference<>(this.connectedAt);
    }

    public UUID getId() {
        return id;
    }

    public Socket getSocket() {
        return socket;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat.get();
    }

    /**
     * Atomically updates the last heartbeat timestamp to now.
     */
    public void updateHeartbeat() {
        lastHeartbeat.set(Instant.now());
    }

    /**
     * Returns the remote address of the connected worker as a string.
     *
     * @return remote address in the form {@code host:port}, or {@code "unknown"} if unavailable
     */
    public String getRemoteAddress() {
        if (socket.getRemoteSocketAddress() instanceof InetSocketAddress addr) {
            return addr.getHostString() + ":" + addr.getPort();
        }
        return "unknown";
    }

    @Override
    public String toString() {
        return "WorkerConnection[id=%s, remote=%s, connectedAt=%s]"
                .formatted(id, getRemoteAddress(), connectedAt);
    }
}
