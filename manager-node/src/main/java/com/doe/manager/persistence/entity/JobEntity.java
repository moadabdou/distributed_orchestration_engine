package com.doe.manager.persistence.entity;

import com.doe.core.model.JobStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a job stored in the {@code jobs} table.
 *
 * <p>The {@code payload} column uses PostgreSQL {@code JSONB} type via
 * {@link JdbcTypeCode} with {@link SqlTypes#JSON}, letting Hibernate 6 handle
 * the JSON type mapping without any custom converter.
 *
 * <p>Timestamps ({@code createdAt}, {@code updatedAt}) are set explicitly
 * from the domain {@link com.doe.core.model.Job} object — the DB acts as an
 * event log and owns no timestamp generation logic.
 */
@Entity
@Table(
    name = "jobs",
    indexes = @Index(name = "idx_jobs_status", columnList = "status")
)
public class JobEntity {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** FK to workers(id) — nullable while the job is PENDING. */
    @Column(name = "worker_id", columnDefinition = "uuid")
    private UUID workerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    private WorkflowEntity workflow;

    @Column(name = "dag_index")
    private Integer dagIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    /** JSON payload stored as PostgreSQL JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "timeout_ms", nullable = false)
    private long timeoutMs;

    @Column(name = "job_label")
    private String jobLabel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected JobEntity() {}

    public JobEntity(UUID id, JobStatus status, String payload, long timeoutMs, String jobLabel, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.status = status;
        this.payload = payload;
        this.timeoutMs = timeoutMs;
        this.jobLabel = jobLabel;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ─── Getters & setters ──────────────────────────────────────────────────

    public UUID getId()           { return id; }
    public UUID getWorkerId()     { return workerId; }
    public JobStatus getStatus()  { return status; }
    public WorkflowEntity getWorkflow() { return workflow; }
    public Integer getDagIndex()  { return dagIndex; }
    public String getPayload()    { return payload; }
    public String getResult()     { return result; }
    public int getRetryCount()    { return retryCount; }
    public long getTimeoutMs()    { return timeoutMs; }
    public String getJobLabel()   { return jobLabel; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setWorkerId(UUID workerId)    { this.workerId = workerId; }
    public void setStatus(JobStatus status)   { this.status = status; }
    public void setWorkflow(WorkflowEntity workflow) { this.workflow = workflow; }
    public void setDagIndex(Integer dagIndex) { this.dagIndex = dagIndex; }
    public void setResult(String result)      { this.result = result; }
    public void setRetryCount(int count)      { this.retryCount = count; }
    public void setTimeoutMs(long timeoutMs)  { this.timeoutMs = timeoutMs; }
    public void setJobLabel(String jobLabel)  { this.jobLabel = jobLabel; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
