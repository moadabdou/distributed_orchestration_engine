package com.doe.core.model;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a connected worker in the distributed orchestration engine.
 * <p>
 * Each instance tracks the worker's unique ID, its TCP socket, and the
 * timestamp of the last received heartbeat. The heartbeat timestamp is
 * updated atomically for lock-free concurrent access.
 */
public class WorkerConnection {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerConnection.class);

    private final UUID id;
    private final Socket socket;
    private final Instant connectedAt;
    private final AtomicReference<Instant> lastHeartbeat;
    
    private final int maxCapacity;
    private final AtomicInteger activeJobCount = new AtomicInteger(0);
    private final Set<UUID> activeJobs = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean inAvailableQueue = new AtomicBoolean(false);

    /**
     * Creates a new worker connection.
     *
     * @param id     unique identifier for this worker
     * @param socket the TCP socket connected to the worker
     */
    public WorkerConnection(UUID id, Socket socket) {
        this(id, socket, 4);
    }

    /**
     * Creates a new worker connection with a specified capacity.
     *
     * @param id          unique identifier for this worker
     * @param socket      the TCP socket connected to the worker
     * @param maxCapacity the maximum number of concurrent jobs this worker can handle
     */
    public WorkerConnection(UUID id, Socket socket, int maxCapacity) {
        if (id == null) {
            throw new IllegalArgumentException("Worker ID must not be null");
        }
        if (socket == null) {
            throw new IllegalArgumentException("Socket must not be null");
        }
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be positive, got: " + maxCapacity);
        }
        this.id = id;
        this.socket = socket;
        this.connectedAt = Instant.now();
        this.lastHeartbeat = new AtomicReference<>(this.connectedAt);
        this.maxCapacity = maxCapacity;
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

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public int getActiveJobCount() {
        return activeJobCount.get();
    }

    public Set<UUID> getActiveJobs() {
        return Collections.unmodifiableSet(activeJobs);
    }
    
    public AtomicBoolean getInAvailableQueue() {
        return inAvailableQueue;
    }

    /**
     * Atomically attempts to reserve capacity on this worker.
     *
     * @return true if capacity was reserved successfully, false if full
     */
    public boolean tryReserveCapacity(UUID jobId) {
        while (true) {
            int current = activeJobCount.get();
            if (current >= maxCapacity) {
                return false;
            }
            if (activeJobCount.compareAndSet(current, current + 1)) {
                activeJobs.add(jobId);
                LOG.info("Job {} assigned to worker {}. Active jobs: {}", jobId, id, current + 1);
                return true;
            }
        }
    }

    /**
     * Releases one unit of capacity and removes the given job.
     */
    public void releaseCapacity(UUID jobId) {
        if (activeJobs.remove(jobId)) {
            int remaining = activeJobCount.decrementAndGet();
            LOG.info("Job {} unassigned from worker {}. Active jobs: {}", jobId, id, remaining);
        }
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
        return "WorkerConnection[id=%s, remote=%s, capacity=%d/%d, connectedAt=%s]"
                .formatted(id, getRemoteAddress(), activeJobCount.get(), maxCapacity, connectedAt);
    }
}
