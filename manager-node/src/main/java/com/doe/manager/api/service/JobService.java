package com.doe.manager.api.service;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
import com.doe.manager.api.dto.JobRequest;
import com.doe.manager.api.dto.JobResponse;
import com.doe.manager.api.exception.ResourceNotFoundException;
import com.doe.manager.metrics.MetricsService;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.scheduler.DagScheduler;
import com.doe.manager.scheduler.JobQueue;
import com.doe.manager.server.ManagerServer;
import com.doe.manager.workflow.WorkflowManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobQueue jobQueue;
    private final ManagerServer managerServer;
    private final MetricsService metricsService;
    private final WorkflowManager workflowManager;
    private final DagScheduler dagScheduler;
    private final long defaultJobTimeoutMs;
    private static final Gson GSON = new Gson();

    public JobService(JobRepository jobRepository, 
                      JobQueue jobQueue, 
                      ManagerServer managerServer, 
                      MetricsService metricsService,
                      WorkflowManager workflowManager,
                      DagScheduler dagScheduler,
                      @org.springframework.beans.factory.annotation.Value("${doe.workflow.default-job-timeout-ms:600000}") long defaultJobTimeoutMs) {
        this.jobRepository = jobRepository;
        this.jobQueue = jobQueue;
        this.managerServer = managerServer;
        this.metricsService = metricsService;
        this.workflowManager = workflowManager;
        this.dagScheduler = dagScheduler;
        this.defaultJobTimeoutMs = defaultJobTimeoutMs;
    }

    @Transactional
    public JobResponse submitJob(JobRequest request) {
        if (request.payload() == null || request.payload().isBlank()) {
            throw new IllegalArgumentException("Job payload must not be empty");
        }
        
        long timeoutMs = request.timeoutMs() != null ? request.timeoutMs() : defaultJobTimeoutMs;

        // Domain object creates ID, sets status to PENDING and timestamps
        Job job = Job.newJob(request.payload())
                .timeoutMs(timeoutMs)
                .jobLabel(request.label())
                .build();

        JobEntity entity = new JobEntity(
                job.getId(),
                job.getStatus(),
                job.getPayload(),
                job.getTimeoutMs(),
                job.getJobLabel(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );

        jobRepository.save(entity);
        jobQueue.enqueue(job);
        metricsService.incrementJobsSubmitted();

        return mapToResponse(entity);
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(UUID id) {
        return jobRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + id));
    }

    @Transactional
    public void cancelJob(UUID id) {
        JobEntity entity = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + id));

        JobStatus status = entity.getStatus();

        if (status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel a job that is already in a terminal state: " + status);
        }

        if (status == JobStatus.PENDING) {
            // Eager cancel in database
            entity.setStatus(JobStatus.CANCELLED);
            entity.setResult("Cancelled via API before assignment");
            entity.setUpdatedAt(java.time.Instant.now());
            jobRepository.save(entity);

            // Eager cancel in memory (JobRegistry contains enqueued jobs)
            managerServer.getJobRegistry().get(id).ifPresent(job -> {
                try {
                    job.setResult("Cancelled via API before assignment");
                    job.transition(JobStatus.CANCELLED);
                } catch (IllegalStateException e) {
                    // Ignore concurrent state transition race
                }
            });

            // Sync with Workflow domain model if it belongs to one
            if (entity.getWorkflow() != null) {
                UUID workflowId = entity.getWorkflow().getId();
                Workflow workflow = workflowManager.getWorkflow(workflowId);
                if (workflow != null) {
                    WorkflowJob wj = workflow.getJob(id);
                    if (wj != null) {
                        try {
                            wj.getJob().setResult("Cancelled via API");
                            wj.getJob().transition(JobStatus.CANCELLED);
                        } catch (IllegalStateException e) {
                            // ignore if already terminal
                        }
                    }
                }
                dagScheduler.onWorkflowJobChanged(workflowId);
            }
            return;
        }
        
        // Assigned or Running means it has a worker ID (or is about to be sent).
        // By changing its memory state, we might cause conflicts, so we safely send the CANCEL_JOB message
        // and allow the worker/manager interaction to clean it up via the JOB_RESULT.
        UUID workerId = entity.getWorkerId();
        if (workerId == null) {
             managerServer.getJobRegistry().get(id).ifPresent(job -> {
                 UUID inMemWorkerId = job.getAssignedWorkerId();
                 if (inMemWorkerId != null) {
                     managerServer.sendCancelJob(inMemWorkerId, id);
                 }
             });
        } else {
             managerServer.sendCancelJob(workerId, id);
        }

        // If it's in a workflow, notify the scheduler even if it's currently running/assigned,
        // so the scheduler is primed for when the result eventually comes back as CANCELLED.
        if (entity.getWorkflow() != null) {
            dagScheduler.onWorkflowJobChanged(entity.getWorkflow().getId());
        }
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> listJobs(int page, int size, JobStatus status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<JobEntity> entityPage;
        if (status != null) {
            entityPage = jobRepository.findByStatus(status, pageable);
        } else {
            entityPage = jobRepository.findAll(pageable);
        }
        return entityPage.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> getJobsByWorkflow(UUID workflowId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "dagIndex"));
        return jobRepository.findByWorkflowId(workflowId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public JobResponse getJobByWorkflowAndLabel(UUID workflowId, String label) {
        return jobRepository.findByWorkflowIdAndJobLabel(workflowId, label)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Job with label '" + label + "' not found in workflow: " + workflowId));
    }

    @Transactional
    public void retryJob(UUID id) {
        JobEntity entity = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + id));

        JobStatus status = entity.getStatus();

        if (status != JobStatus.FAILED && status != JobStatus.CANCELLED) {
            throw new IllegalStateException("Can only retry jobs that are in FAILED or CANCELLED status, but was: " + status);
        }

        // 1. Update DB Record
        entity.setStatus(JobStatus.PENDING);
        entity.setResult(null);
        entity.setWorkerId(null);
        entity.setRetryCount(entity.getRetryCount() + 1);
        entity.setUpdatedAt(java.time.Instant.now());
        jobRepository.save(entity);

        // 2. Sync with Memory/Workflow if applicable
        if (entity.getWorkflow() != null) {
            UUID workflowId = entity.getWorkflow().getId();
            Workflow workflow = workflowManager.getWorkflow(workflowId);
            if (workflow != null) {
                WorkflowJob wj = workflow.getJob(id);
                if (wj != null) {
                    try {
                        // Reset memory state in the workflow job
                        Job job = wj.getJob();
                        job.transition(JobStatus.PENDING);
                        job.setResult(null);
                        
                        // Clear from scheduler's "already submitted" tracker
                        dagScheduler.forgetJob(workflowId, id);
                        
                        // Resume workflow if it was terminal
                        if (workflow.getStatus() == com.doe.core.model.WorkflowStatus.COMPLETED || 
                            workflow.getStatus() == com.doe.core.model.WorkflowStatus.FAILED) {
                            workflowManager.resumeWorkflow(workflowId);
                        }
                    } catch (IllegalStateException e) {
                        // ignore if already terminal in a way we didn't expect
                    }
                }
            }
            // Trigger scheduler tick for this workflow
            dagScheduler.onWorkflowJobChanged(workflowId);
        } else {
            // Standalone job retry
            Job job = Job.newJob(entity.getPayload())
                    .id(entity.getId())
                    .workflowId(entity.getWorkflow() != null ? entity.getWorkflow().getId() : null)
                    .status(JobStatus.PENDING)
                    .timeoutMs(entity.getTimeoutMs())
                    .jobLabel(entity.getJobLabel())
                    .build();
            jobQueue.enqueue(job);
        }
    }

    @Transactional(readOnly = true)
    public List<String> getJobLogs(UUID jobId, Integer start, Integer length) {
        Path logFile = Paths.get("data", "var", "logs", "jobs", jobId.toString() + ".log");
        if (!Files.exists(logFile)) {
            throw new ResourceNotFoundException("Logs not found for job: " + jobId);
        }
        try {
            String rawLogs = Files.readString(logFile, StandardCharsets.UTF_8);
            List<String> logLines = GSON.fromJson(rawLogs, new TypeToken<List<String>>(){}.getType());
            
            if (logLines == null) {
                return List.of();
            }
            
            int fromIndex = (start != null) ? Math.max(0, start) : 0;
            if (fromIndex >= logLines.size()) {
                return List.of();
            }
            
            int toIndex;
            if (length != null && length > 0) {
                toIndex = Math.min(fromIndex + length, logLines.size());
            } else {
                toIndex = logLines.size();
            }
            
            return logLines.subList(fromIndex, toIndex);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read logs for job: " + jobId, e);
        }
    }

    private JobResponse mapToResponse(JobEntity entity) {
        return new JobResponse(
                entity.getId(),
                entity.getStatus(),
                entity.getJobLabel(),
                entity.getPayload(),
                entity.getResult(),
                entity.getWorkerId(),
                entity.getWorkflow() != null ? entity.getWorkflow().getId() : null,
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
