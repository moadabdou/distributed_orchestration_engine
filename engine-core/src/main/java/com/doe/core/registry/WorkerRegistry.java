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
 * {@link LinkedBlockingQueue} as an idle-worker fast lane.
 *
 * <h3>Idle-queue invariant</h3>
 * <ul>
 *   <li>Every worker enters the queue exactly once, on {@link #register}.</li>
 *   <li>It is re-offered exactly once each time it returns to IDLE via {@link #markIdle}.</li>
 *   <li>{@link #findIdle} is the only consumer; it validates entries to handle
 *       stale references left by {@link #unregister}/{@link #unregisterIfSame}.</li>
 * </ul>
 */
public class WorkerRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerRegistry.class);

    private final ConcurrentHashMap<UUID, WorkerConnection> workers = new ConcurrentHashMap<>();

    /**
     * Idle-only fast lane.
     * Unbounded in practice (bounded only by the number of connected workers).
     */
    private final LinkedBlockingQueue<WorkerConnection> idleQueue = new LinkedBlockingQueue<>();

    // ──── Registration ────────────────────────────────────────────────────────

    /**
     * Registers a worker connection and immediately makes it available for
     * scheduling by placing it in the idle queue.
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
        idleQueue.offer(connection);
        return previous;
    }

    /**
     * Removes a worker from the registry.
     * <p>
     * Any stale entry left in the idle queue is drained lazily inside
     * {@link #findIdle()}.
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
     * exact same object as {@code expected}. Prevents a stale handler thread
     * from removing a newer re-registration's connection.
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

    // ──── Idle-queue operations ───────────────────────────────────────────────

    /**
     * Blocks until an IDLE worker is available and returns it in the BUSY state.
     * <p>
     * Stale entries (workers that disconnected while in the queue) are silently
     * discarded and the wait resumes. If the CAS in
     * {@link WorkerConnection#trySetBusy()} ever fails (indicating a double-offer
     * programming bug), the entry is discarded with a warning.
     *
     * @return the reserved (now BUSY) connection — never {@code null}
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public WorkerConnection findIdle() throws InterruptedException {
        while (true) {
            WorkerConnection w = idleQueue.take();

            // Discard stale entries from disconnected workers
            if (!workers.containsKey(w.getId())) {
                LOG.debug("Discarding stale idle-queue entry for disconnected worker {}", w.getId());
                continue;
            }

            // CAS: guard against double-offer bug (should never be false in correct usage)
            if (w.trySetBusy()) {
                return w;
            }

            LOG.warn("CAS failed for worker {} — possible double-offer bug; discarding entry", w.getId());
        }
    }

    /**
     * Marks a worker as IDLE and re-offers it to the idle queue, making it
     * immediately available to the scheduler.
     * <p>
     * This is the <b>only</b> correct way to return a worker to the idle state.
     * Direct calls to {@link WorkerConnection#setIdle()} bypass the queue and
     * will cause the scheduler to block indefinitely waiting for a worker that
     * never appears.
     *
     * @param workerId the UUID of the worker to mark idle
     */
    public void markIdle(UUID workerId) {
        WorkerConnection w = workers.get(workerId);
        if (w == null) {
            LOG.warn("markIdle called for unknown or already-removed worker {}", workerId);
            return;
        }
        w.setIdle();
        idleQueue.offer(w);
        LOG.debug("Worker {} returned to idle queue", workerId);
    }

    // ──── Lookups ─────────────────────────────────────────────────────────────

    /**
     * Looks up a worker by its UUID.
     *
     * @param workerId the UUID to look up
     * @return an {@link Optional} containing the connection if found
     */
    public Optional<WorkerConnection> get(UUID workerId) {
        return Optional.ofNullable(workers.get(workerId));
    }

    /**
     * Returns an unmodifiable snapshot of all registered workers.
     *
     * @return unmodifiable map of worker UUID → connection
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
