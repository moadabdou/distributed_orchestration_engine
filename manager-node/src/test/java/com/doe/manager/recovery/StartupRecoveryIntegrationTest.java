package com.doe.manager.recovery;

import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkerStatus;
import com.doe.core.registry.JobRegistry;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.entity.WorkerEntity;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.persistence.repository.WorkerRepository;
import com.doe.manager.scheduler.JobQueue;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link StartupRecoveryService}.
 * <p>
 * Spins up a real PostgreSQL container via Testcontainers, runs Flyway migrations,
 * pre-seeds the DB with orphaned jobs and stale workers, then calls
 * {@code recover()} and asserts that the DB and in-memory state are correctly rebuilt.
 *
 * <p>{@code @DirtiesContext} ensures a fresh Spring context (and thus an empty registry
 * and queue) for each test method.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StartupRecoveryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired StartupRecoveryService recoveryService;
    @Autowired JobRepository          jobRepository;
    @Autowired WorkerRepository       workerRepository;
    @Autowired JobQueue               jobQueue;
    @Autowired JobRegistry            jobRegistry;

    // ─── helpers ──────────────────────────────────────────────────────────────

    private WorkerEntity saveWorker(WorkerStatus status) {
        WorkerEntity w = new WorkerEntity(
                UUID.randomUUID(), "host-" + UUID.randomUUID(), "10.0.0.1",
                status, Instant.now(), Instant.now());
        return workerRepository.save(w);
    }

    private JobEntity saveJob(UUID workerId, JobStatus status) {
        JobEntity e = new JobEntity(
                UUID.randomUUID(), status, "{\"type\":\"test\"}", Instant.now(), Instant.now());
        e.setWorkerId(workerId);
        return jobRepository.save(e);
    }

    // ─── tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Orphaned ASSIGNED and RUNNING jobs are reset to PENDING in DB and enqueued in-memory")
    void recover_resetsOrphansAndRebuildsQueue() {
        // Arrange: 2 workers (IDLE, BUSY), 3 jobs (ASSIGNED, RUNNING, PENDING)
        WorkerEntity w1 = saveWorker(WorkerStatus.IDLE);
        WorkerEntity w2 = saveWorker(WorkerStatus.BUSY);

        JobEntity assigned = saveJob(w1.getId(), JobStatus.ASSIGNED);
        JobEntity running  = saveJob(w2.getId(), JobStatus.RUNNING);
        JobEntity pending  = saveJob(null,        JobStatus.PENDING);  // already OK — should NOT be re-enqueued

        // Act
        recoveryService.recover();

        // ── DB assertions ──────────────────────────────────────────────────────

        // Both formerly-in-flight jobs must be PENDING with no worker
        JobEntity dbAssigned = jobRepository.findById(assigned.getId()).orElseThrow();
        assertEquals(JobStatus.PENDING, dbAssigned.getStatus());
        assertNull(dbAssigned.getWorkerId());

        JobEntity dbRunning = jobRepository.findById(running.getId()).orElseThrow();
        assertEquals(JobStatus.PENDING, dbRunning.getStatus());
        assertNull(dbRunning.getWorkerId());

        // The already-PENDING job must be untouched
        JobEntity dbPending = jobRepository.findById(pending.getId()).orElseThrow();
        assertEquals(JobStatus.PENDING, dbPending.getStatus());

        // Both workers must be OFFLINE
        assertEquals(WorkerStatus.OFFLINE, workerRepository.findById(w1.getId()).orElseThrow().getStatus());
        assertEquals(WorkerStatus.OFFLINE, workerRepository.findById(w2.getId()).orElseThrow().getStatus());

        // ── In-memory assertions ───────────────────────────────────────────────

        // Only the 2 orphaned jobs were enqueued (not the already-PENDING one)
        assertEquals(2, jobQueue.size(),
                "Queue should hold exactly the 2 recovered orphaned jobs");
        assertEquals(2, jobRegistry.size(),
                "Registry should contain exactly the 2 recovered jobs");

        // Recovered jobs must be findable in the registry with correct state
        assertTrue(jobRegistry.get(assigned.getId()).isPresent());
        assertEquals(JobStatus.PENDING, jobRegistry.get(assigned.getId()).get().getStatus());
        assertNull(jobRegistry.get(assigned.getId()).get().getAssignedWorkerId());

        assertTrue(jobRegistry.get(running.getId()).isPresent());
        assertEquals(JobStatus.PENDING, jobRegistry.get(running.getId()).get().getStatus());
    }

    @Test
    @DisplayName("No workers in DB → recovery runs without error")
    void recover_noWorkersNorJobs_isNoOp() {
        // Arrange: empty DB (Flyway already gave us clean tables)
        recoveryService.recover();

        assertEquals(0, jobQueue.size());
        assertEquals(0, jobRegistry.size());
    }

    @Test
    @DisplayName("retryCount from DB is preserved across recovery")
    void recover_preservesRetryCount() {
        WorkerEntity w = saveWorker(WorkerStatus.BUSY);
        JobEntity entity = saveJob(w.getId(), JobStatus.RUNNING);
        entity.setRetryCount(2);
        jobRepository.save(entity);

        recoveryService.recover();

        assertEquals(2, jobRegistry.get(entity.getId()).orElseThrow().getRetryCount());
    }
}
