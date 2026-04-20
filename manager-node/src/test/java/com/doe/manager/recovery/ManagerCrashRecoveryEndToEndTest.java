package com.doe.manager.recovery;

import org.springframework.test.context.ActiveProfiles;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.registry.JobRegistry;
import com.doe.manager.persistence.entity.JobEntity;
import com.doe.manager.persistence.repository.JobRepository;
import com.doe.manager.persistence.repository.WorkerRepository;
import com.doe.manager.scheduler.JobQueue;
import com.doe.manager.scheduler.JobScheduler;
import com.doe.manager.server.ManagerServer;
import com.doe.worker.client.WorkerClient;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end chaos + persistence test.
 * <p>
 * Combines worker crash recovery with PostgreSQL persistence to validate the full
 * lifecycle: submit jobs → let some reach RUNNING → kill Manager abruptly →
 * restart Manager → verify orphaned jobs are recovered from DB and completed by a worker.
 * <p>
 * This bridges the gap between:
 * <ul>
 *   <li>{@link StartupRecoveryIntegrationTest} — tests DB recovery in isolation (no live jobs)</li>
 *   <li>{@link com.doe.manager.server.CrashRecoveryIntegrationTest} — tests worker crash recovery (no DB)</li>
 * </ul>
 * <p>
 * Scenario: Manager process crash while jobs are in-flight → restart → zero jobs lost.
 */
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ManagerCrashRecoveryEndToEndTest {

    private static final String JWT_SECRET = "3c34e62a26514757c2c159851f50a80d46dddc7fa0a06df5c689f928e4e9b94z";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ManagerServer        server;
    @Autowired JobScheduler         jobScheduler;
    @Autowired JobQueue             jobQueue;
    @Autowired JobRegistry          jobRegistry;
    @Autowired JobRepository        jobRepository;
    @Autowired WorkerRepository     workerRepository;
    @Autowired StartupRecoveryService recoveryService;

    private final List<WorkerClient> workers = new ArrayList<>();
    private final List<Thread> workerThreads = new ArrayList<>();

    // ─── helpers ──────────────────────────────────────────────────────────────

    private String generateToken() {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private WorkerClient startWorker() throws InterruptedException {
        int initialSize = server.getRegistry().size();
        WorkerClient worker = new WorkerClient(
                "localhost", server.getLocalPort(), 2000, 10_000, generateToken());
        Thread t = Thread.ofVirtual().start(worker::start);

        // Wait for registration
        long deadline = System.currentTimeMillis() + 5_000;
        while (server.getRegistry().size() == initialSize && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(server.getRegistry().size() > initialSize, "Worker should have registered");

        workers.add(worker);
        workerThreads.add(t);
        return worker;
    }

    private void shutdownAllWorkers() throws InterruptedException {
        for (WorkerClient w : workers) {
            w.shutdown();
        }
        for (Thread t : workerThreads) {
            t.join(3_000);
        }
        workers.clear();
        workerThreads.clear();
    }

    /**
     * Wait until ALL given jobs reach one of the expected statuses.
     */
    private boolean awaitAllDb(List<Job> jobs, long timeoutMs, JobStatus... expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            boolean allDone = jobs.stream().allMatch(j -> {
                JobEntity dbJob = jobRepository.findById(j.getId()).orElse(null);
                if (dbJob == null) return false;
                for (JobStatus s : expected) {
                    if (dbJob.getStatus() == s) return true;
                }
                return false;
            });
            if (allDone) return true;
            Thread.sleep(200);
        }
        return false;
    }

    // ─── tests ────────────────────────────────────────────────────────────────

    @AfterEach
    void cleanup() throws Exception {
        try { shutdownAllWorkers(); } catch (Exception ignored) {}
        // Ensure server is running for the next test (DirtiesContext handles isolation,
        // but we clean up workers to be safe)
        if (!server.isRunning()) {
            try {
                // If the test crashed the server, restart it so @DirtiesContext can tear down cleanly
                // The DB is already torn down by DirtiesContext, so this is just for cleanup
                recoveryService.recover();
                server.start();
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("10 jobs submitted, 3 workers running → kill Manager mid-flight → restart → all jobs complete, zero lost")
    void managerCrashMidExecution_restartRecoverAndComplete() throws Exception {
        // ── Phase 1: Normal operation — start workers and submit jobs ─────────
        // Start 3 workers
        startWorker();
        startWorker();
        startWorker();
        assertEquals(3, server.getRegistry().size(), "Should have 3 workers registered");

        // Submit 10 sleep jobs (long enough that some will be RUNNING when we crash)
        List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Job job = Job.newJob("{\"type\":\"sleep\",\"ms\":2000}").timeoutMs(60000L).build();
            jobs.add(job);
            JobEntity entity = new JobEntity(job.getId(), job.getStatus(), job.getPayload(), job.getTimeoutMs(), "test", job.getCreatedAt(), job.getUpdatedAt());
            jobRepository.save(entity);
            jobQueue.enqueue(job);
        }

        // Wait until at least some jobs are COMPLETED and some are RUNNING (meaning workers are actively executing)
        long deadline = System.currentTimeMillis() + 15_000;
        long runningCount = 0;
        long completedBeforeCrash = 0;
        while (System.currentTimeMillis() < deadline) {
            runningCount = jobs.stream().filter(j -> j.getStatus() == JobStatus.RUNNING).count();
            completedBeforeCrash = jobs.stream().filter(j -> j.getStatus() == JobStatus.COMPLETED).count();
            if (completedBeforeCrash >= 5 && runningCount >= 1) break;
            Thread.sleep(200);
        }
        assertTrue(completedBeforeCrash >= 5, "Expected at least 5 jobs COMPLETED before crash, got " + completedBeforeCrash);
        assertTrue(runningCount >= 1, "Expected at least 1 job RUNNING before crash, got " + runningCount);

        // Capture DB state before crash — jobs that are ASSIGNED or RUNNING should be in the DB
        long inFlightBeforeCrash = jobs.stream()
                .filter(j -> j.getStatus() == JobStatus.ASSIGNED || j.getStatus() == JobStatus.RUNNING)
                .count();
        assertTrue(inFlightBeforeCrash > 0, "Should have in-flight jobs before crash");

        // ── Phase 2: Simulate Manager crash — abrupt shutdown, no graceful cleanup ─
        // This simulates the Manager process being killed (SIGKILL, OOM, etc.)
        // Jobs in ASSIGNED/RUNNING state will remain in the DB as orphaned.
        server.shutdown();
        Thread.sleep(500); // Let the server fully stop

        assertFalse(server.isRunning(), "Manager should be stopped after crash");
        
        // Wait to drain the in-memory queue to simulate it being gone
        while (jobQueue.dequeue() != null) {}
        
        recoveryService.recover();

        // After recovery, orphaned jobs should be PENDING in DB and in the in-memory queue
        for (JobEntity e : jobRepository.findAll()) {
            if (e.getStatus() == JobStatus.ASSIGNED || e.getStatus() == JobStatus.RUNNING) {
                fail("After recovery, no jobs should be ASSIGNED or RUNNING in DB. Found: " + e.getId());
            }
        }
        
        long expectedRecoveredJobs = 20 - completedBeforeCrash;
        assertEquals(expectedRecoveredJobs, jobQueue.size(),
                "Queue should contain exactly the " + expectedRecoveredJobs + " unfinished jobs after recovery resets");
        // We'll skip the exact registry size check for in-flight because recovery just puts everything to PENDING in the DB

        // Start the server again (scheduler loop needs to be running to assign jobs)
        server.start();
        assertTrue(server.isRunning(), "Manager should be running after restart");

        // ── Phase 4: Connect a worker and verify all jobs complete ────────────
        startWorker();
        assertEquals(1, server.getRegistry().size(), "Should have 1 worker registered on restarted server");

        // Wait for ALL 20 jobs to reach COMPLETED
        boolean allCompleted = awaitAllDb(jobs, 60_000, JobStatus.COMPLETED);
        
        List<JobEntity> finalDbJobs = jobRepository.findAll();
        assertTrue(allCompleted,
                "Not all jobs completed. Final statuses: " +
                finalDbJobs.stream().map(j -> j.getStatus().name()).collect(Collectors.joining(", ")));

        // Verify zero jobs lost: every job is either COMPLETED or FAILED (max retries exhausted)
        long completed = finalDbJobs.stream().filter(j -> j.getStatus() == JobStatus.COMPLETED).count();
        long failed = finalDbJobs.stream().filter(j -> j.getStatus() == JobStatus.FAILED).count();
        assertEquals(20, completed + failed,
                "All 20 jobs should be accounted for (COMPLETED + FAILED), got " + (completed + failed));

        // At least some jobs should have been recovered (retried after crash)
        long recoveredCount = finalDbJobs.stream().filter(j -> j.getRetryCount() > 0).count();
        assertTrue(recoveredCount > 0,
                "At least some jobs should have been retried after recovery. Recovered: " + recoveredCount);
    }
}
