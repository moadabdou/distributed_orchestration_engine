package com.doe.manager.scheduler;

import com.doe.core.event.EngineEventListener;
import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.registry.JobRegistry;
import com.doe.core.registry.WorkerDeathListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Recovers jobs that were assigned to a worker that died or disconnected.
 * Implements {@link WorkerDeathListener} to receive callbacks from the {@link com.doe.manager.server.ManagerServer}.
 * <p>
 * Fires {@link EngineEventListener#onJobRequeued} and {@link EngineEventListener#onJobFailed}
 * after each state transition so that the DB is kept in sync.
 */
@Component
public class CrashRecoveryHandler implements WorkerDeathListener {

    private static final Logger LOG = LoggerFactory.getLogger(CrashRecoveryHandler.class);
    private final JobRegistry jobRegistry;
    private final JobQueue jobQueue;
    private final EngineEventListener eventListener;

    public CrashRecoveryHandler(JobRegistry jobRegistry, JobQueue jobQueue, EngineEventListener eventListener) {
        this.jobRegistry = jobRegistry;
        this.jobQueue = jobQueue;
        this.eventListener = eventListener;
    }

    @Override
    public void onWorkerDeath(UUID workerId, java.util.Set<UUID> activeJobs) {
        java.util.List<Job> affectedJobs = activeJobs.stream().map(jobRegistry::get).filter(java.util.Optional::isPresent).map(java.util.Optional::get).collect(java.util.stream.Collectors.toList());
        int recovered = 0;

        for (Job job : affectedJobs) {
            JobStatus status = job.getStatus();
            if (status == JobStatus.ASSIGNED || status == JobStatus.RUNNING) {
                job.incrementRetryCount();
                if (job.getRetryCount() <= 3) {
                    try {
                        job.transition(JobStatus.PENDING);
                        job.setAssignedWorkerId(null);
                        // Update DB BEFORE making job available in queue to prevent race condition
                        eventListener.onJobRequeued(job.getId(), job.getRetryCount(), job.getUpdatedAt());
                        jobQueue.requeue(job); // Add to the front of the line
                        recovered++;
                        LOG.info("Job {} transitioned back to PENDING (retry {}/3)", job.getId(), job.getRetryCount());
                    } catch (IllegalStateException e) {
                        LOG.error("Failed to recover job {}", job.getId(), e);
                    }
                } else {
                    job.transition(JobStatus.FAILED);
                    job.setResult("Failed after exceeding max retries (3) due to worker deaths.");
                    UUID failedWorkerId = job.getAssignedWorkerId();  // Capture before clear
                    job.setAssignedWorkerId(null);
                    LOG.warn("Job {} FAILED after exceeding max retries.", job.getId());
                    eventListener.onJobFailed(job.getId(), failedWorkerId, job.getResult(), job.getUpdatedAt());
                }
            }
        }

        if (recovered > 0 || !affectedJobs.isEmpty()) {
            LOG.info("Recovered {} jobs from dead worker {}", recovered, workerId);
        }
    }

    /**
     * Re-queues a single timed-out job. Exposes the recovery logic to the {@link JobTimeoutMonitor}.
     */
    public void recoverTimedOutJob(Job job) {
        job.incrementRetryCount();
        if (job.getRetryCount() <= 3) {
            try {
                job.transition(JobStatus.PENDING);
                UUID oldWorkerId = job.getAssignedWorkerId();
                job.setAssignedWorkerId(null);
                // Update DB BEFORE making job available in queue to prevent race condition
                eventListener.onJobRequeued(job.getId(), job.getRetryCount(), job.getUpdatedAt());
                jobQueue.requeue(job);
                LOG.info("Job {} timed out on worker {} and transitioned back to PENDING (retry {}/3)",
                         job.getId(), oldWorkerId, job.getRetryCount());
            } catch (IllegalStateException e) {
                LOG.error("Failed to recover timed-out job {}", job.getId(), e);
            }
        } else {
            try {
                job.transition(JobStatus.FAILED);
                job.setResult("Failed after exceeding max retries (3) due to timeouts.");
                UUID failedWorkerId = job.getAssignedWorkerId();  // Capture before clear
                job.setAssignedWorkerId(null);
                LOG.warn("Job {} FAILED after exceeding max retries due to timeouts.", job.getId());
                eventListener.onJobFailed(job.getId(), failedWorkerId, job.getResult(), job.getUpdatedAt());
            } catch (IllegalStateException e) {
                 LOG.error("Failed to fail timed-out job {}", job.getId(), e);
            }
        }
    }
}
