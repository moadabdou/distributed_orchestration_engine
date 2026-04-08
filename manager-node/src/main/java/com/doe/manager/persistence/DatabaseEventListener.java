package com.doe.manager.persistence;

import com.doe.core.event.EngineEventListener;
import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkerStatus;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.entity.WorkerEntity;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.persistence.repository.WorkerRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Persists engine state-change events to the database via {@link WorkerRepository}
 * and {@link JobRepository}.
 *
 * <h3>Heartbeat buffering</h3>
 * Heartbeats arrive very frequently (one per worker every few seconds). Writing
 * a DB row on every heartbeat would amplify DB writes unnecessarily. Instead, this
 * listener accumulates the latest timestamp per worker in a {@link ConcurrentHashMap}
 * and a background thread flushes the buffer every {@value HEARTBEAT_FLUSH_INTERVAL_MS} ms
 * using a targeted {@code UPDATE} query that does not re-load the full entity.
 *
 * <h3>Thread safety</h3>
 * All non-heartbeat methods are annotated {@link Transactional} and called from
 * virtual threads in the engine. Spring's proxy wraps each call in a dedicated
 * transaction. The heartbeat buffer uses a {@link ConcurrentHashMap} (last-write-wins
 * semantics are correct for timestamps) and is flushed from a single-thread executor.
 */
@Component
public class DatabaseEventListener implements EngineEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseEventListener.class);
    private static final long HEARTBEAT_FLUSH_INTERVAL_MS = 2_000;

    private final WorkerRepository workerRepository;
    private final JobRepository jobRepository;
    private final TransactionTemplate transactionTemplate;

    /** Pending heartbeat timestamps keyed by workerId. Last write wins. */
    private final ConcurrentHashMap<UUID, Instant> pendingHeartbeats = new ConcurrentHashMap<>();

    private ScheduledExecutorService heartbeatFlusher;

    public DatabaseEventListener(
            WorkerRepository workerRepository,
            JobRepository jobRepository,
            TransactionTemplate transactionTemplate) {
        this.workerRepository = workerRepository;
        this.jobRepository = jobRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @PostConstruct
    public void init() {

        this.heartbeatFlusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-db-flusher");
            t.setDaemon(true);
            return t;
        });


        this.heartbeatFlusher.scheduleAtFixedRate(
                this::flushHeartbeats,
                HEARTBEAT_FLUSH_INTERVAL_MS,
                HEARTBEAT_FLUSH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        LOG.info("DatabaseEventListener started (heartbeat flush interval: {} ms)", HEARTBEAT_FLUSH_INTERVAL_MS);
    }

    @PreDestroy
    public void shutdown() {
        // Final flush before shutdown so no heartbeat is lost
        flushHeartbeats();
        heartbeatFlusher.shutdownNow();
        LOG.info("DatabaseEventListener shut down");
    }

    // ─── Worker events ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public void onWorkerRegistered(UUID workerId, String hostname, String ipAddress, int maxCapacity, Instant registeredAt) {
        workerRepository.findById(workerId).ifPresentOrElse(entity -> {
            entity.setHostname(hostname);
            entity.setIpAddress(ipAddress);
            entity.setStatus(WorkerStatus.ONLINE);
            entity.setMaxCapacity(maxCapacity);
            entity.setLastHeartbeat(registeredAt);
            workerRepository.save(entity);
            LOG.debug("DB: updated worker {} (hostname={}, ip={}, capacity={})", workerId, hostname, ipAddress, maxCapacity);
        }, () -> {
            WorkerEntity entity = new WorkerEntity(
                    workerId,
                    hostname,
                    ipAddress,
                    WorkerStatus.ONLINE,
                    maxCapacity,
                    registeredAt,
                    registeredAt
            );
            workerRepository.save(entity);
            LOG.debug("DB: inserted worker {} (hostname={}, ip={}, capacity={})", workerId, hostname, ipAddress, maxCapacity);
        });
    }

    /**
     * Accumulates the heartbeat timestamp in-memory; the background flusher writes to DB.
     */
    @Override
    public void onWorkerHeartbeat(UUID workerId, Instant timestamp) {
        pendingHeartbeats.put(workerId, timestamp);
    }

    @Override
    @Transactional
    public void onWorkerDied(UUID workerId) {
        try {
            workerRepository.findById(workerId).ifPresentOrElse(entity -> {
                entity.setStatus(WorkerStatus.OFFLINE);
                workerRepository.save(entity);
                LOG.debug("DB: worker {} → OFFLINE", workerId);
            }, () -> LOG.warn("DB: onWorkerDied — worker {} not found", workerId));
        } catch (org.springframework.dao.InvalidDataAccessApiUsageException | 
                 org.springframework.orm.jpa.JpaSystemException |
                 java.lang.IllegalStateException e) {
            // EntityManager is likely closed during application shutdown.
            LOG.debug("DB: EntityManager closed when recording worker {} death: {}", workerId, e.getMessage());
        } catch (Exception e) {
            LOG.warn("DB: failed to record worker {} death", workerId, e);
        }
    }

    // ─── Job events ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void onJobAssigned(UUID jobId, UUID workerId, Instant updatedAt) {
        updateJobEntity(jobId, entity -> {
            entity.setStatus(JobStatus.ASSIGNED);
            entity.setWorkerId(workerId);
            entity.setUpdatedAt(updatedAt);
        }, "ASSIGNED");
        
        adjustWorkerJobCount(workerId, 1);
    }

    @Override
    @Transactional
    public void onJobRunning(UUID jobId, Instant updatedAt) {
        updateJobEntity(jobId, entity -> {
            entity.setStatus(JobStatus.RUNNING);
            entity.setUpdatedAt(updatedAt);
        }, "RUNNING");
    }

    @Override
    @Transactional
    public void onJobCompleted(UUID jobId, String result, Instant updatedAt) {
        jobRepository.findById(jobId).ifPresent(entity -> {
            UUID workerId = entity.getWorkerId();
            entity.setStatus(JobStatus.COMPLETED);
            entity.setResult(result);
            entity.setUpdatedAt(updatedAt);
            jobRepository.save(entity);
            LOG.debug("DB: job {} → COMPLETED", jobId);
            
            if (workerId != null) adjustWorkerJobCount(workerId, -1);
        });
    }

    @Override
    @Transactional
    public void onJobFailed(UUID jobId, String result, Instant updatedAt) {
        jobRepository.findById(jobId).ifPresent(entity -> {
            UUID workerId = entity.getWorkerId();
            entity.setStatus(JobStatus.FAILED);
            entity.setResult(result);
            entity.setUpdatedAt(updatedAt);
            jobRepository.save(entity);
            LOG.debug("DB: job {} → FAILED", jobId);
            
            if (workerId != null) adjustWorkerJobCount(workerId, -1);
        });
    }

    @Override
    @Transactional
    public void onJobRequeued(UUID jobId, int retryCount, Instant updatedAt) {
        jobRepository.findById(jobId).ifPresent(entity -> {
            UUID workerId = entity.getWorkerId();
            entity.setStatus(JobStatus.PENDING);
            entity.setWorkerId(null);
            entity.setRetryCount(retryCount);
            entity.setUpdatedAt(updatedAt);
            jobRepository.save(entity);
            LOG.debug("DB: job {} → PENDING (requeued, retry={})", jobId, retryCount);
            
            if (workerId != null) adjustWorkerJobCount(workerId, -1);
        });
    }

    // ─── Internals ───────────────────────────────────────────────────────────

    private void adjustWorkerJobCount(UUID workerId, int delta) {
        workerRepository.findById(workerId).ifPresentOrElse(worker -> {
            worker.setActiveJobCount(Math.max(0, worker.getActiveJobCount() + delta));
            workerRepository.save(worker);
            LOG.debug("DB: adjusted activeJobCount for worker {} by {} (now {})", workerId, delta, worker.getActiveJobCount());
        }, () -> LOG.warn("DB: worker {} not found when adjusting activeJobCount", workerId));
    }

    /**
     * Applies a mutation to a {@link JobEntity} and saves the result.
     * Logs a warning if the entity is not found (should never happen in normal operation).
     */
    private void updateJobEntity(UUID jobId, java.util.function.Consumer<JobEntity> mutator, String description) {
        jobRepository.findById(jobId).ifPresentOrElse(entity -> {
            mutator.accept(entity);
            jobRepository.save(entity);
            LOG.debug("DB: job {} → {}", jobId, description);
        }, () -> LOG.warn("DB: job {} not found when trying to update to {}", jobId, description));
    }

    /**
     * Flushes accumulated heartbeat timestamps to the DB using targeted UPDATE queries.
     * Runs on the {@code heartbeat-db-flusher} thread every {@value HEARTBEAT_FLUSH_INTERVAL_MS} ms.
     */
    public void flushHeartbeats() {
        if (pendingHeartbeats.isEmpty()) {
            return;
        }

        // Drain the map atomically — snapshot all entries and remove them
        int flushed = 0;
        for (Map.Entry<UUID, Instant> entry : pendingHeartbeats.entrySet()) {
            UUID workerId = entry.getKey();
            Instant ts = pendingHeartbeats.remove(workerId);
            if (ts != null) {
                try {

                    transactionTemplate.executeWithoutResult(status -> {
                        workerRepository.updateHeartbeat(workerId, ts);
                    });

                    flushed++;
                } catch (org.springframework.dao.InvalidDataAccessApiUsageException | 
                         org.springframework.orm.jpa.JpaSystemException |
                         java.lang.IllegalStateException e) {
                    // This typically happens during shutdown when the EntityManager is already closed.
                    // We log at DEBUG level to avoid alarming stack traces during normal shutdown.
                    LOG.debug("DB: EntityManager closed during heartbeat flush for worker {}: {}", workerId, e.getMessage());
                    // Put it back in case the process isn't actually dead (unlikely, but safe)
                    pendingHeartbeats.putIfAbsent(workerId, ts);
                } catch (Exception e) {
                    LOG.warn("DB: failed to flush heartbeat for worker {}", workerId, e);
                    pendingHeartbeats.putIfAbsent(workerId, ts);
                }
            }
        }

        if (flushed > 0) {
            LOG.debug("DB: flushed {} heartbeat(s)", flushed);
        }
    }
}
