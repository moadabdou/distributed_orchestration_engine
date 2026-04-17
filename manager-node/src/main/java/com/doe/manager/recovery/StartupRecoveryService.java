package com.doe.manager.recovery;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkerStatus;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.persistence.repository.WorkerRepository;
import com.doe.manager.scheduler.JobQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Recovers orphaned in-flight jobs from the database when the Manager restarts.
 *
 * <h3>What it does</h3>
 * <ol>
 *   <li>Marks <em>all</em> workers {@code OFFLINE} — any worker that was connected at crash
 *       time has lost its TCP connection and will have to re-register.</li>
 *   <li>Finds every job whose last persisted state was {@code ASSIGNED} or {@code RUNNING}
 *       (i.e., jobs the Manager handed out but never received a result for).</li>
 *   <li>Resets each such job to {@code PENDING} (clears {@code worker_id}) and re-enqueues
 *       it into the in-memory {@link JobQueue} + {@link com.doe.core.registry.JobRegistry},
 *       restoring the scheduler's view of work-to-be-done.</li>
 * </ol>
 *
 * <h3>Lifecycle</h3>
 * Triggered by {@link ApplicationReadyEvent}, which fires after the full Spring context —
 * including the {@link com.doe.manager.server.ManagerServer} and {@link com.doe.manager.scheduler.JobScheduler} —
 * is ready. Using {@code ApplicationReadyEvent} (rather than {@code @PostConstruct}) ensures
 * the scheduler loop is alive and can immediately pick up the recovered jobs.
 *
 * <h3>DB writes</h3>
 * This service writes to the DB directly (it is the source of truth at this point).
 * It does <em>not</em> fire {@link com.doe.core.event.EngineEventListener} events, which
 * would cause redundant second writes for each recovered job.
 *
 * <h3>Timeout</h3>
 * {@code timeoutMs} is not persisted in {@link JobEntity}. Recovered jobs are assigned
 * the default value of 60 000 ms. This is acceptable for the current scope.
 */
@Component
public class StartupRecoveryService {

    private static final Logger LOG = LoggerFactory.getLogger(StartupRecoveryService.class);

    /** Default job timeout used for reconstructed domain objects (not stored in DB). */
    private static final long DEFAULT_TIMEOUT_MS = 60_000L;

    private final JobRepository jobRepository;
    private final WorkerRepository workerRepository;
    private final JobQueue jobQueue;

    public StartupRecoveryService(JobRepository jobRepository,
                                  WorkerRepository workerRepository,
                                  JobQueue jobQueue) {
        this.jobRepository  = jobRepository;
        this.workerRepository = workerRepository;
        this.jobQueue       = jobQueue;
    }

    /**
     * Entry point for startup recovery. Runs inside a single transaction so the
     * bulk worker update and all job resets are committed atomically.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recover() {
        // ── 1. Mark all workers OFFLINE ──────────────────────────────────────
        int workersReset = workerRepository.updateAllStatuses(WorkerStatus.OFFLINE);
        LOG.info("Startup recovery: marked {} worker(s) OFFLINE", workersReset);

        // ── 2. Reset orphaned in-flight jobs (ASSIGNED / RUNNING → PENDING) ───
        List<JobEntity> orphans = jobRepository.findByStatusInAndWorkflowIsNull(
                List.of(JobStatus.ASSIGNED, JobStatus.RUNNING));

        Instant now = Instant.now();
        for (JobEntity entity : orphans) {
            entity.setStatus(JobStatus.PENDING);
            entity.setWorkerId(null);
            entity.setUpdatedAt(now);
            jobRepository.save(entity);
        }
        if (!orphans.isEmpty()) {
            LOG.info("Startup recovery: reset {} orphaned standalone job(s) to PENDING", orphans.size());
        }

        // ── 3. Load ALL PENDING jobs into the in-memory queue / registry ──────
        // This covers both jobs that were already PENDING before the crash
        // and the orphans we just reset above (findByStatus re-reads from DB
        // so those are included).
        List<JobEntity> pending = jobRepository.findByStatusAndWorkflowIsNull(JobStatus.PENDING);

        for (JobEntity entity : pending) {
            Job job = Job.newJob(entity.getPayload())
                    .id(entity.getId())
                    .retryCount(entity.getRetryCount())
                    .timeoutMs(DEFAULT_TIMEOUT_MS)
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .build();

            // enqueue() also calls jobRegistry.register() internally.
            jobQueue.enqueue(job);
        }

        LOG.info("Startup recovery: reset {} job(s) to PENDING", pending.size());
    }
}
