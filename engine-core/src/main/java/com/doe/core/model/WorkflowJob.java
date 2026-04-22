package com.doe.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Wraps a {@link Job} with DAG-specific metadata: a positional index within
 * the workflow and an immutable list of dependency job IDs that must complete
 * before this job becomes eligible for scheduling.
 */
public final class WorkflowJob {

    private final int dagIndex;
    private final Job job;
    private final List<UUID> dependencies;
    private final List<UUID> dataDependencies;

    private WorkflowJob(Builder builder) {
        this.dagIndex         = builder.dagIndex;
        this.job              = Objects.requireNonNull(builder.job, "job");
        this.dependencies     = Collections.unmodifiableList(
                Objects.requireNonNull(builder.dependencies, "dependencies"));
        this.dataDependencies = Collections.unmodifiableList(
                Objects.requireNonNull(builder.dataDependencies, "dataDependencies"));
    }

    /**
     * Returns a builder pre-initialised with the given {@link Job}.
     */
    public static Builder fromJob(Job job) {
        return new Builder().job(job);
    }

    /**
     * Zero-based index of this node inside the workflow DAG.
     * Useful for topological ordering and scheduling heuristics.
     */
    public int getDagIndex() {
        return dagIndex;
    }

    /**
     * The underlying {@link Job} that will be dispatched to a worker.
     */
    public Job getJob() {
        return job;
    }

    /**
     * Immutable list of job IDs that are prerequisites for this job.
     * Returns an empty list if there are no dependencies (root node).
     */
    public List<UUID> getDependencies() {
        return dependencies;
    }

    /**
     * Immutable list of job IDs that share a data/signaling relationship with this job.
     */
    public List<UUID> getDataDependencies() {
        return dataDependencies;
    }

    @Override
    public String toString() {
        return "WorkflowJob{dagIndex=%d, jobId=%s, deps=[%s], dataDeps=[%s]}"
                .formatted(dagIndex, job.getId(),
                        dependencies.stream().map(UUID::toString).collect(Collectors.joining(", ")),
                        dataDependencies.stream().map(UUID::toString).collect(Collectors.joining(", ")));
    }

    // ──── Builder ────────────────────────────────────────────────────────────

    public static final class Builder {
        private int dagIndex;
        private Job job;
        private List<UUID> dependencies = List.of();
        private List<UUID> dataDependencies = List.of();

        private Builder() {}

        public Builder dagIndex(int dagIndex)             { this.dagIndex = dagIndex;           return this; }
        public Builder job(Job job)                       { this.job = job;                     return this; }
        public Builder dependencies(List<UUID> deps)      { this.dependencies = deps;           return this; }
        public Builder dataDependencies(List<UUID> deps)  { this.dataDependencies = deps;       return this; }

        public WorkflowJob build() { return new WorkflowJob(this); }
    }
}
