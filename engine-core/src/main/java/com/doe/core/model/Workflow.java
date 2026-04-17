package com.doe.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root representing a DAG-based workflow.
 *
 * <p>A workflow owns an ordered collection of {@link WorkflowJob} instances
 * and tracks its lifecycle via {@link WorkflowStatus}. The job map is keyed
 * by each job's {@link UUID} for O(1) lookup during scheduling.
 *
 * <p>This class is immutable — state mutations (status transitions) are
 * handled by a future {@code WorkflowManager} which will manage thread safety.
 */
public final class Workflow {

    private final UUID id;
    private final String name;
    private final WorkflowStatus status;
    private final Map<UUID, WorkflowJob> jobs;
    private final Instant createdAt;

    private Workflow(Builder builder) {
        this.id        = Objects.requireNonNull(builder.id, "id");
        this.name      = Objects.requireNonNull(builder.name, "name");
        this.status    = Objects.requireNonNull(builder.status, "status");
        this.jobs      = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(builder.jobs, "jobs")));
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt");
    }

    /**
     * Returns a new builder initialised with a generated workflow ID and
     * {@link WorkflowStatus#DRAFT} status.
     */
    public static Builder newWorkflow(String name) {
        return new Builder()
                .id(UUID.randomUUID())
                .name(name)
                .status(WorkflowStatus.DRAFT)
                .createdAt(Instant.now());
    }

    public UUID getId()                    { return id; }
    public String getName()                { return name; }
    public WorkflowStatus getStatus()      { return status; }
    public Instant getCreatedAt()          { return createdAt; }

    /**
     * Returns an unmodifiable view of all workflow jobs in insertion order.
     */
    public List<WorkflowJob> getJobs() {
        return List.copyOf(jobs.values());
    }

    /**
     * Looks up a workflow job by its underlying job ID.
     *
     * @return the {@link WorkflowJob}, or {@code null} if not found.
     */
    public WorkflowJob getJob(UUID jobId) {
        return jobs.get(jobId);
    }

    /**
     * Returns the total number of jobs in this workflow.
     */
    public int jobCount() {
        return jobs.size();
    }

    /**
     * Returns a new {@link Workflow} instance with the given status,
     * preserving all other fields.
     *
     * @param newStatus the new status for this workflow
     * @return a new workflow with the specified status
     */
    public Workflow withStatus(WorkflowStatus newStatus) {
        return new Builder()
                .id(this.id)
                .name(this.name)
                .status(newStatus)
                .addJobs(this.jobs.values())
                .createdAt(this.createdAt)
                .build();
    }

    @Override
    public String toString() {
        return "Workflow{id=%s, name='%s', status=%s, jobs=%d, createdAt=%s}"
                .formatted(id, name, status, jobs.size(), createdAt);
    }

    // ──── Builder ────────────────────────────────────────────────────────────

    public static final class Builder {
        private UUID id;
        private String name;
        private WorkflowStatus status;
        private final Map<UUID, WorkflowJob> jobs = new LinkedHashMap<>();
        private Instant createdAt;

        private Builder() {}

        public Builder id(UUID id)                        { this.id = id;               return this; }
        public Builder name(String name)                  { this.name = name;           return this; }
        public Builder status(WorkflowStatus status)      { this.status = status;       return this; }
        public Builder createdAt(Instant createdAt)       { this.createdAt = createdAt; return this; }

        /**
         * Adds a single {@link WorkflowJob} to this workflow.
         * The job's underlying ID is used as the map key.
         */
        public Builder addJob(WorkflowJob workflowJob) {
            Objects.requireNonNull(workflowJob, "workflowJob");
            this.jobs.put(workflowJob.getJob().getId(), workflowJob);
            return this;
        }

        /**
         * Adds all workflow jobs from the given collection.
         */
        public Builder addJobs(Iterable<WorkflowJob> workflowJobs) {
            Objects.requireNonNull(workflowJobs, "workflowJobs");
            for (WorkflowJob wj : workflowJobs) {
                addJob(wj);
            }
            return this;
        }

        public Workflow build() { return new Workflow(this); }
    }
}
