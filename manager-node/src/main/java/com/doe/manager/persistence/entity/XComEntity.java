package com.doe.manager.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "xcoms",
    indexes = {
        @Index(name = "idx_xcoms_workflow_key", columnList = "workflow_id, xcom_key"),
        @Index(name = "idx_xcoms_job_id", columnList = "job_id")
    }
)
public class XComEntity {

    @Id
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowEntity workflow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private JobEntity job;

    @Column(name = "xcom_key", nullable = false)
    private String key;

    @Column(name = "xcom_value", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "xcom_type", nullable = false, length = 50)
    private String type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected XComEntity() {}

    public XComEntity(UUID id, WorkflowEntity workflow, JobEntity job, String key, String value, String type, Instant createdAt) {
        this.id = id;
        this.workflow = workflow;
        this.job = job;
        this.key = key;
        this.value = value;
        this.type = type;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public WorkflowEntity getWorkflow() { return workflow; }
    public JobEntity getJob() { return job; }
    public String getKey() { return key; }
    public String getValue() { return value; }
    public String getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }
}
