package com.doe.core.registry;

import com.doe.core.model.WorkerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread-safe registry of connected workers.
 * <p>
 * Backed by a {@link ConcurrentHashMap} for O(1) lookup by UUID and a
 * {@link LinkedBlockingQueue} as an available-worker fast lane.
 */
public class WorkerRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerRegistry.class);

    private final ConcurrentHashMap<UUID, WorkerConnection> workers = new ConcurrentHashMap<>();

    /**
     * Available-only fast lane.
     */
    private final LinkedBlockingQueue<WorkerConnection> availableQueue = new LinkedBlockingQueue<>();

    // ──── Registration ────────────────────────────────────────────────────────

    /**
     * Registers a worker connection and immediately makes it available for
     * scheduling by placing it in the available queue.
     *
     * @param connection the worker connection to register
     * @return the previous connection for this UUID, or {@code null} if none
     * @throws IllegalArgumentException if connection is null
     */
    public WorkerConnection register(WorkerConnection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("WorkerConnection must not be null");
        }
        WorkerConnection previous = workers.put(connection.getId(), connection);
        offerAvailableWorker(connection);
        return previous;
    }

    /**
     * Removes a worker from the registry.
     *
     * @param workerId the UUID of the worker to remove
     * @return the removed connection, or {@code null} if not found
     */
    public WorkerConnection unregister(UUID workerId) {
        if (workerId == null) {
            return null;
        }
        return workers.remove(workerId);
    }

    /**
     * Conditionally removes a worker only if the current registry entry is the
     * exact same object as {@code expected}.
     *
     * @param workerId the UUID of the worker to remove
     * @param expected the connection instance that must match the current entry
     * @return {@code true} if the entry was removed
     */
    public boolean unregisterIfSame(UUID workerId, WorkerConnection expected) {
        if (workerId == null || expected == null) {
            return false;
        }
        return workers.remove(workerId, expected);
    }

    // ──── Available-queue operations ───────────────────────────────────────────────

    /**
     * Blocks until an available worker is found with capacity.
     *
     * @return the available connection — never {@code null}
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public WorkerConnection findAvailableWorker(UUID jobId) throws InterruptedException {
        while (true) {
            WorkerConnection w = availableQueue.take();
            w.getInAvailableQueue().set(false); // No longer in queue.

            // Discard stale entries from disconnected workers
            if (!workers.containsKey(w.getId())) {
                LOG.debug("Discarding stale available-queue entry for disconnected worker {}", w.getId());
                continue;
            }

            if (w.tryReserveCapacity(jobId)) {
                // If worker still has remaining capacity, safely re-offer it to the available queue
                offerAvailableWorker(w);
                return w;
            }
        }
    }

    /**
     * Releases capacity on the worker. If it creates available space and is not currently
     * in the available-queue, re-enqueue it.
     *
     * @param workerId the UUID of the worker
     * @param jobId    the UUID of the job being released
     */
    public void releaseCapacity(UUID workerId, UUID jobId) {
        WorkerConnection w = workers.get(workerId);
        if (w == null) {
            LOG.warn("releaseCapacity called for unknown or already-removed worker {}", workerId);
            return;
        }
        w.releaseCapacity(jobId);
        offerAvailableWorker(w);
    }

    /**
     * Internal helper to offer worker safely.
     */
    public void offerAvailableWorker(WorkerConnection w) {
        if (w.getActiveJobCount() < w.getMaxCapacity()) {
            if (w.getInAvailableQueue().compareAndSet(false, true)) {
                availableQueue.offer(w);
                LOG.debug("Worker {} returned to available queue", w.getId());
            }
        }
    }

    // ──── Lookups ─────────────────────────────────────────────────────────────

    /**
     * Looks up a worker by its UUID.
     */
    public Optional<WorkerConnection> get(UUID workerId) {
        return Optional.ofNullable(workers.get(workerId));
    }

    /**
     * Returns an unmodifiable snapshot of all registered workers.
     */
    public Map<UUID, WorkerConnection> getAll() {
        return Collections.unmodifiableMap(workers);
    }

    /**
     * Returns the current number of registered workers.
     */
    public int size() {
        return workers.size();
    }

    /**
     * Returns {@code true} if the registry contains no workers.
     */
    public boolean isEmpty() {
        return workers.isEmpty();
    }
}
