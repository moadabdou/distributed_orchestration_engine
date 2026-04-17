package com.doe.manager.workflow;

import com.doe.core.model.Job;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
import com.doe.manager.persistence.entity.JobDependencyEntity;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.entity.WorkflowEntity;
import com.doe.manager.persistence.repository.JobDependencyRepository;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.persistence.repository.WorkflowRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class WorkflowPersistenceListener implements WorkflowEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowPersistenceListener.class);

    private final WorkflowManager workflowManager;
    private final WorkflowRepository workflowRepository;
    private final JobRepository jobRepository;
    private final JobDependencyRepository jobDependencyRepository;

    public WorkflowPersistenceListener(
            WorkflowManager workflowManager,
            WorkflowRepository workflowRepository,
            JobRepository jobRepository,
            JobDependencyRepository jobDependencyRepository) {
        this.workflowManager = workflowManager;
        this.workflowRepository = workflowRepository;
        this.jobRepository = jobRepository;
        this.jobDependencyRepository = jobDependencyRepository;
    }

    @PostConstruct
    public void init() {
        // this listener is can be used only when the application is fully started so we cant allow the manager to use it before that 
        workflowManager.addListener(this);
        LOG.info("WorkflowPersistenceListener registered to WorkflowManager");
    }

    @Override
    @Transactional
    public void onWorkflowRegistered(Workflow workflow) {
        insertWorkflowFully(workflow);
    }

    @Override
    @Transactional
    public void onWorkflowDeleted(UUID workflowId) {
        // Cascade delete is usually defined in DB schema/JPA, but let's be explicit
        workflowRepository.deleteById(workflowId);
        LOG.debug("DB: Deleted workflow {}", workflowId);
    }

    @Override
    @Transactional
    public void onWorkflowUpdated(Workflow workflow) {
        // Full update: update workflow, update/re-insert jobs and dependencies
        // Safe approach: delete jobs (and dependencies cascade) and re-insert 
        // Or update them. Let's update existing and insert new.
        updateWorkflowEntity(workflow);
        updateJobsAndDependencies(workflow);
    }

    @Override
    @Transactional
    public void onWorkflowExecuted(Workflow workflow) {
        updateWorkflowStatus(workflow);
    }

    @Override
    @Transactional
    public void onWorkflowPaused(Workflow workflow) {
        updateWorkflowStatus(workflow);
    }

    @Override
    @Transactional
    public void onWorkflowResumed(Workflow workflow) {
        updateWorkflowStatus(workflow);
    }

    @Override
    @Transactional
    public void onWorkflowReset(Workflow workflow) {
        updateWorkflowStatus(workflow);
        // Also reset job statuses
        for (WorkflowJob wj : workflow.getJobs()) {
            jobRepository.findById(wj.getJob().getId()).ifPresent(je -> {
                je.setStatus(com.doe.core.model.JobStatus.PENDING);
                je.setUpdatedAt(Instant.now());
                jobRepository.save(je);
            });
        }
    }

    @Override
    @Transactional
    public void onWorkflowStatusChanged(Workflow workflow) {
        updateWorkflowStatus(workflow);
    }

    // --- Helpers ---

    private void insertWorkflowFully(Workflow workflow) {
        WorkflowEntity we = new WorkflowEntity(
                workflow.getId(),
                workflow.getName(),
                workflow.getStatus(),
                workflow.getCreatedAt(),
                Instant.now() // updated at
        );
        workflowRepository.save(we);

        // Insert jobs
        for (WorkflowJob wj : workflow.getJobs()) {
            Job job = wj.getJob();
            JobEntity je = new JobEntity(
                    job.getId(),
                    job.getStatus(),
                    job.getPayload(),
                    job.getCreatedAt(),
                    job.getUpdatedAt() != null ? job.getUpdatedAt() : Instant.now()
            );
            je.setWorkflow(we);
            je.setDagIndex(wj.getDagIndex());
            jobRepository.save(je);
        }

        // Insert dependencies
        for (WorkflowJob wj : workflow.getJobs()) {
            JobEntity dependent = jobRepository.findById(wj.getJob().getId()).orElseThrow();
            for (UUID depId : wj.getDependencies()) {
                JobEntity dependsOn = jobRepository.findById(depId).orElseThrow();
                JobDependencyEntity dependency = new JobDependencyEntity(dependent, dependsOn);
                jobDependencyRepository.save(dependency);
            }
        }
        LOG.debug("DB: Inserted workflow {} with {} jobs", workflow.getId(), workflow.getJobs().size());
    }

    private void updateWorkflowEntity(Workflow workflow) {
        workflowRepository.findById(workflow.getId()).ifPresent(we -> {
            we.setName(workflow.getName());
            we.setStatus(workflow.getStatus());
            we.setUpdatedAt(Instant.now());
            workflowRepository.save(we);
            LOG.debug("DB: Updated workflow {}", workflow.getId());
        });
    }

    private void updateWorkflowStatus(Workflow workflow) {
        workflowRepository.findById(workflow.getId()).ifPresent(we -> {
            we.setStatus(workflow.getStatus());
            we.setUpdatedAt(Instant.now());
            workflowRepository.save(we);
            LOG.debug("DB: Workflow {} status -> {}", workflow.getId(), workflow.getStatus());
        });
    }

    private void updateJobsAndDependencies(Workflow workflow) {
        // Simplest: update workflow jobs if they exist, insert if they don't.
        // For an update workflow, the structure might have changed. So clear dependencies and recreate.
        WorkflowEntity we = workflowRepository.findById(workflow.getId()).orElse(null);
        if (we == null) return;

        for (WorkflowJob wj : workflow.getJobs()) {
            Job job = wj.getJob();
            JobEntity je = jobRepository.findById(job.getId()).orElse(new JobEntity(
                job.getId(), job.getStatus(), job.getPayload(), job.getCreatedAt(), Instant.now()
            ));
            je.setWorkflow(we);
            je.setDagIndex(wj.getDagIndex());
            je.setStatus(job.getStatus());
            je.setUpdatedAt(Instant.now());
            jobRepository.save(je);
            
            // Delete old dependencies containing this job as dependent
            jobDependencyRepository.deleteByDependentJobId(job.getId());
        }

        // Must flush / ensure dependencies are deleted before inserting new ones.
        // Spring Data JPA usually handles this within the transaction, but we can just insert them now
        for (WorkflowJob wj : workflow.getJobs()) {
            JobEntity dependent = jobRepository.findById(wj.getJob().getId()).orElseThrow();
            for (UUID depId : wj.getDependencies()) {
                JobEntity dependsOn = jobRepository.findById(depId).orElseThrow();
                JobDependencyEntity dependency = new JobDependencyEntity(dependent, dependsOn);
                jobDependencyRepository.save(dependency);
            }
        }
    }
}
