package com.doe.core.registry;

import com.doe.core.model.WorkerConnection;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of connected workers.
 * <p>
 * Backed by a {@link ConcurrentHashMap} for lock-free reads and
 * high-throughput heartbeat tracking.
 */
public class WorkerRegistry {

    private final ConcurrentHashMap<UUID, WorkerConnection> workers = new ConcurrentHashMap<>();

    /**
     * Registers a worker connection.
     *
     * @param connection the worker connection to register
     * @return the previous connection for this UUID, or {@code null} if none
     * @throws IllegalArgumentException if connection is null
     */
    public WorkerConnection register(WorkerConnection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("WorkerConnection must not be null");
        }
        return workers.put(connection.getId(), connection);
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
}
