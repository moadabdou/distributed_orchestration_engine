package com.doe.core.registry;

import com.doe.core.model.Job;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for tracking all {@link Job} instances across the cluster.
 */
public class JobRegistry {

    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    /**
     * Registers a job in the registry.
     *
     * @param job the job to register
     * @throws IllegalArgumentException if the job is null
     */
    public void register(Job job) {
        if (job == null) {
            throw new IllegalArgumentException("Job cannot be null");
        }
        jobs.put(job.getId(), job);
    }

    /**
     * Retrieves a job by its UUID.
     *
     * @param id the UUID of the job
     * @return an {@link Optional} containing the job if found, otherwise empty
     */
    public Optional<Job> get(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(jobs.get(id));
    }

    /**
     * Returns an unmodifiable view of all registered jobs.
     */
    public Map<UUID, Job> getAll() {
        return Collections.unmodifiableMap(jobs);
    }

    /**
     * Returns a collection of all registered jobs.
     */
    public Collection<Job> values() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    /**
     * Returns the number of jobs in the registry.
     */
    public int size() {
        return jobs.size();
    }

    /**
     * Returns a list of jobs currently assigned to the given workerId.
     */
    public java.util.List<Job> findByWorker(UUID workerId) {
        if (workerId == null) return Collections.emptyList();
        return jobs.values().stream()
                .filter(job -> workerId.equals(job.getAssignedWorkerId()))
                .toList();
    }
}
