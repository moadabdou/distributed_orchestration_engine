package com.doe.manager.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "data_dependencies")
public class DataDependencyEntity {

    @EmbeddedId
    private DataDependencyId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("sourceJobId")
    @JoinColumn(name = "source_job_id")
    private JobEntity sourceJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("targetJobId")
    @JoinColumn(name = "target_job_id")
    private JobEntity targetJob;

    protected DataDependencyEntity() {}

    public DataDependencyEntity(JobEntity sourceJob, JobEntity targetJob) {
        this.id = new DataDependencyId(sourceJob.getId(), targetJob.getId());
        this.sourceJob = sourceJob;
        this.targetJob = targetJob;
    }

    public DataDependencyId getId() { return id; }
    public void setId(DataDependencyId id) { this.id = id; }

    public JobEntity getSourceJob() { return sourceJob; }
    public void setSourceJob(JobEntity sourceJob) { this.sourceJob = sourceJob; }

    public JobEntity getTargetJob() { return targetJob; }
    public void setTargetJob(JobEntity targetJob) { this.targetJob = targetJob; }
}
