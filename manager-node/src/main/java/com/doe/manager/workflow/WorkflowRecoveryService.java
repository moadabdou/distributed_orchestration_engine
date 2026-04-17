package com.doe.manager.workflow;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
import com.doe.core.model.WorkflowStatus;
import com.doe.manager.persistence.entity.JobDependencyEntity;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.entity.WorkflowEntity;
import com.doe.manager.persistence.repository.JobDependencyRepository;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.persistence.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkflowRecoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowRecoveryService.class);

    private final WorkflowManager workflowManager;
    private final WorkflowRepository workflowRepository;
    private final JobRepository jobRepository;
    private final JobDependencyRepository jobDependencyRepository;
    private final String recoveryMode;

    public WorkflowRecoveryService(
            WorkflowManager workflowManager,
            WorkflowRepository workflowRepository,
            JobRepository jobRepository,
            JobDependencyRepository jobDependencyRepository,
            @Value("${doe.workflow.recovery-mode:PAUSED_ON_RESTART}") String recoveryMode) {
        this.workflowManager = workflowManager;
        this.workflowRepository = workflowRepository;
        this.jobRepository = jobRepository;
        this.jobDependencyRepository = jobDependencyRepository;
        this.recoveryMode = recoveryMode;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverWorkflows() {
        LOG.info("Starting workflow recovery. Recovery mode: {}", recoveryMode);

        List<WorkflowEntity> workflowEntities = workflowRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt"));
        
        int draftCount = 0, pausedCount = 0, failedCount = 0, completedCount = 0;

        for (WorkflowEntity we : workflowEntities) {
            Workflow workflow = reconstructWorkflow(we);

            // Apply recovery rules
            WorkflowStatus recoveredStatus = workflow.getStatus();
            if (recoveredStatus == WorkflowStatus.RUNNING) {
                if ("PAUSED_ON_RESTART".equalsIgnoreCase(recoveryMode)) {
                    recoveredStatus = WorkflowStatus.PAUSED;
                }
            }

            // Important: jobs statuses inside the workflow might also be affected if we wanted to enforce strictly,
            // but jobs are usually left as-is (RUNNING might naturally timeout or be caught by a CrashRecoveryHandler).
            // Here we simply enforce the workflow's configured resumed state and register it.
            Workflow recoveredWorkflow = workflow.withStatus(recoveredStatus);
            workflowManager.registerRecoveredWorkflow(recoveredWorkflow);

            switch (recoveredStatus) {
                case DRAFT: draftCount++; break;
                case PAUSED: pausedCount++; break;
                case FAILED: failedCount++; break;
                case COMPLETED: completedCount++; break;
                default: break;
            }
        }

        LOG.info("Recovered {} workflows ({} DRAFT, {} PAUSED, {} FAILED, {} COMPLETED)",
                workflowEntities.size(), draftCount, pausedCount, failedCount, completedCount);
    }

    private Workflow reconstructWorkflow(WorkflowEntity we) {
        List<JobEntity> jobEntities = jobRepository.findByWorkflowId(we.getId());
        List<WorkflowJob> workflowJobs = new ArrayList<>();

        for (JobEntity je : jobEntities) {
            // Reset non-terminal (ASSIGNED, RUNNING) jobs to PENDING
            if (je.getStatus() == JobStatus.ASSIGNED || je.getStatus() == JobStatus.RUNNING) {
                je.setStatus(JobStatus.PENDING);
                je.setWorkerId(null);
                je.setUpdatedAt(java.time.Instant.now());
                jobRepository.save(je);
                LOG.info("Workflow recovery: reset non-terminal job {} to PENDING", je.getId());
            }

            Job job = Job.newJob(je.getPayload())
                         .id(je.getId())
                         .status(je.getStatus())
                         .timeoutMs(60000) // Defaulting to 0 since timeoutMs is not physically stored in jobEntity right now
                         .createdAt(je.getCreatedAt())
                         .updatedAt(je.getUpdatedAt())
                         .build();
            // In the core engine, Job object timeoutMs isn't currently persisted in DB Phase1 Entities. 
            // We use default or 0, or we'd need to modify JobEntity. For now 0 is fine.
                         
            List<JobDependencyEntity> dependencyEntities = jobDependencyRepository.findByDependentJobId(je.getId());
            List<UUID> dependencies = dependencyEntities.stream()
                    .map(dep -> dep.getDependsOn().getId())
                    .collect(Collectors.toList());

            WorkflowJob wj = WorkflowJob.fromJob(job)
                    .dagIndex(je.getDagIndex())
                    .dependencies(dependencies)
                    .build();

            workflowJobs.add(wj);
        }

        return Workflow.newWorkflow(we.getName())
                .id(we.getId())
                .status(we.getStatus())
                .createdAt(we.getCreatedAt())
                .addJobs(workflowJobs)
                .build();
    }
}
