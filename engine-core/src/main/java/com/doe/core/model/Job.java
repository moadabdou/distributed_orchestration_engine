package com.doe.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing a unit of work dispatched to a worker node.
 *
 * <p>State transitions are enforced by {@link #transition(JobStatus)};
 * any attempt to move to an invalid state throws {@link IllegalStateException}.
 */
public final class Job {

    // ──── Fields ────────────────────────────────────────────────────────────

    private final UUID id;
    private final UUID workflowId; // nullable for legacy/standalone jobs
    private volatile JobStatus status;
    private final String payload;        // JSON command / data
    private volatile String result;      // output from worker (nullable)
    private volatile UUID assignedWorkerId; // nullable
    private final int retryCount;
    private final long timeoutMs;
    private final String jobLabel; // nullable
    private final Instant createdAt;
    private volatile Instant updatedAt;

    // ──── Private constructor (use builder) ──────────────────────────────────

    private Job(Builder builder) {
        this.id               = Objects.requireNonNull(builder.id,        "id");
        this.workflowId       = builder.workflowId;
        this.status           = Objects.requireNonNull(builder.status,    "status");
        this.payload          = Objects.requireNonNull(builder.payload,   "payload");
        this.result           = builder.result;
        this.assignedWorkerId = builder.assignedWorkerId;
        this.retryCount       = builder.retryCount;
        this.timeoutMs        = builder.timeoutMs;
        this.jobLabel         = builder.jobLabel;
        this.createdAt        = Objects.requireNonNull(builder.createdAt, "createdAt");
        this.updatedAt        = Objects.requireNonNull(builder.updatedAt, "updatedAt");
    }

    // ──── Factory / Builder ──────────────────────────────────────────────────

    /**
     * Returns a new {@link Builder} initialised with the given payload.
     * The job id is generated automatically; {@code status} defaults to
     * {@link JobStatus#PENDING} and timestamps to {@link Instant#now()}.
     */
    public static Builder newJob(String payload) {
        Instant now = Instant.now();
        return new Builder()
                .id(UUID.randomUUID())
                .status(JobStatus.PENDING)
                .retryCount(0)
                .payload(payload)
                .createdAt(now)
                .updatedAt(now);
    }

    // ──── State machine ──────────────────────────────────────────────────────

    /**
     * Transitions this job to {@code target}.
     *
     * @param target the desired next status
     * @throws IllegalStateException    if the transition is not permitted by
     *                                  the state machine
     * @throws NullPointerException     if {@code target} is null
     */
    public synchronized void transition(JobStatus target) {
        Objects.requireNonNull(target, "target status must not be null");
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Invalid job state transition: %s → %s".formatted(status, target));
        }
        this.status    = target;
        this.updatedAt = Instant.now();
    }

    // ──── Accessors ──────────────────────────────────────────────────────────

    public UUID getId()              { return id; }
    public UUID getWorkflowId()      { return workflowId; }
    public JobStatus getStatus()     { return status; }
    public String getPayload()       { return payload; }
    public String getResult()        { return result; }
    public UUID getAssignedWorkerId(){ return assignedWorkerId; }
    public int getRetryCount()       { return retryCount; }
    public long getTimeoutMs()       { return timeoutMs; }
    public String getJobLabel()      { return jobLabel; }
    public Instant getCreatedAt()    { return createdAt; }
    public Instant getUpdatedAt()    { return updatedAt; }

    public synchronized void setResult(String result)              { this.result = result; this.updatedAt = Instant.now(); }
    public synchronized void setAssignedWorkerId(UUID workerId)    { this.assignedWorkerId = workerId; this.updatedAt = Instant.now(); }
    public synchronized void incrementRetryCount()                 { this.retryCount++; this.updatedAt = Instant.now(); }

    // ──── Object ─────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Job{id=%s, status=%s, assignedWorker=%s, createdAt=%s}"
                .formatted(id, status, assignedWorkerId, createdAt);
    }

    // ──── Builder ────────────────────────────────────────────────────────────

    public static final class Builder {
        private UUID id;
        private UUID workflowId;
        private JobStatus status;
        private String payload;
        private String result;
        private UUID assignedWorkerId;
        private int retryCount = 0;
        private long timeoutMs = -1; // -1 indicates not set
        private String jobLabel;
        private Instant createdAt;
        private Instant updatedAt;

        private Builder() {}

        public Builder id(UUID id)                            { this.id = id;                         return this; }
        public Builder workflowId(UUID workflowId)            { this.workflowId = workflowId;         return this; }
        public Builder status(JobStatus status)               { this.status = status;                 return this; }
        public Builder payload(String payload)                { this.payload = payload;               return this; }
        public Builder result(String result)                  { this.result = result;                 return this; }
        public Builder assignedWorkerId(UUID assignedWorkerId){ this.assignedWorkerId = assignedWorkerId; return this; }
        public Builder retryCount(int retryCount)             { this.retryCount = retryCount;         return this; }
        public Builder timeoutMs(long timeoutMs)              { this.timeoutMs = timeoutMs;           return this; }
        public Builder jobLabel(String jobLabel)              { this.jobLabel = jobLabel;             return this; }
        public Builder createdAt(Instant createdAt)           { this.createdAt = createdAt;           return this; }
        public Builder updatedAt(Instant updatedAt)           { this.updatedAt = updatedAt;           return this; }

        public Job build() { 
            if (timeoutMs <= 0) {
                throw new IllegalStateException("Job timeoutMs must be positive (was: " + timeoutMs + ")");
            }
            return new Job(this); 
        }
    }
}
