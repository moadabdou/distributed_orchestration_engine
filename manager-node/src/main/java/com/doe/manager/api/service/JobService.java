package com.doe.manager.api.service;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.manager.api.dto.JobRequest;
import com.doe.manager.api.dto.JobResponse;
import com.doe.manager.api.exception.ResourceNotFoundException;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.scheduler.JobQueue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final JobQueue jobQueue;

    public JobService(JobRepository jobRepository, JobQueue jobQueue) {
        this.jobRepository = jobRepository;
        this.jobQueue = jobQueue;
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

        return mapToResponse(entity);
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(UUID id) {
        return jobRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> listJobs(int page, int size, JobStatus status) {
        Pageable pageable = PageRequest.of(page, size);
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
