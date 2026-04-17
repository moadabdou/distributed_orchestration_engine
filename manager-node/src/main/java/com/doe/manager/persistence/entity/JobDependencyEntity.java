package com.doe.manager.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "job_dependencies")
public class JobDependencyEntity {

    @EmbeddedId
    private JobDependencyId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("dependentJobId")
    @JoinColumn(name = "dependent_job_id")
    private JobEntity dependentJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("dependsOnId")
    @JoinColumn(name = "depends_on_id")
    private JobEntity dependsOn;

    protected JobDependencyEntity() {}

    public JobDependencyEntity(JobEntity dependentJob, JobEntity dependsOn) {
        this.id = new JobDependencyId(dependentJob.getId(), dependsOn.getId());
        this.dependentJob = dependentJob;
        this.dependsOn = dependsOn;
    }

    public JobDependencyId getId() { return id; }
    public void setId(JobDependencyId id) { this.id = id; }

    public JobEntity getDependentJob() { return dependentJob; }
    public void setDependentJob(JobEntity dependentJob) { this.dependentJob = dependentJob; }

    public JobEntity getDependsOn() { return dependsOn; }
    public void setDependsOn(JobEntity dependsOn) { this.dependsOn = dependsOn; }
}
