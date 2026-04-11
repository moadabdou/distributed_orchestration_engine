package com.doe.manager.api.service;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.manager.api.dto.JobRequest;
import com.doe.manager.api.dto.JobResponse;
import com.doe.manager.api.exception.ResourceNotFoundException;
import com.doe.manager.metrics.MetricsService;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.scheduler.JobQueue;
import com.doe.manager.server.ManagerServer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobQueue jobQueue;
    private final ManagerServer managerServer;
    private final MetricsService metricsService;

    public JobService(JobRepository jobRepository, JobQueue jobQueue, ManagerServer managerServer, MetricsService metricsService) {
        this.jobRepository = jobRepository;
        this.jobQueue = jobQueue;
        this.managerServer = managerServer;
        this.metricsService = metricsService;
    }

    @Transactional
    public JobResponse submitJob(JobRequest request) {
        if (request.payload() == null || request.payload().isBlank()) {
            throw new IllegalArgumentException("Job payload must not be empty");
        }

        // Domain object creates ID, sets status to PENDING and timestamps
        Job job = Job.newJob(request.payload()).build();

        JobEntity entity = new JobEntity(
                job.getId(),
                job.getStatus(),
                job.getPayload(),
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

            // Eager cancel in memory so JobScheduler drops it
            managerServer.getJobRegistry().get(id).ifPresent(job -> {
                try {
                    job.transition(JobStatus.CANCELLED);
                    job.setResult("Cancelled via API before assignment");
                } catch (IllegalStateException e) {
                    // Ignore concurrent state transition race
                }
            });
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

    private JobResponse mapToResponse(JobEntity entity) {
        return new JobResponse(
                entity.getId(),
                entity.getStatus(),
                entity.getPayload(),
                entity.getResult(),
                entity.getWorkerId(),
                entity.getRetryCount(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
