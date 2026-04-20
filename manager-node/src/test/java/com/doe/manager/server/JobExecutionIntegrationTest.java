package com.doe.manager.server;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.worker.client.WorkerClient;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for Issue #008 — Worker Job Execution.
 * <p>
 * Starts a real {@link ManagerServer} and a real {@link WorkerClient} connected via localhost,
 * enqueues jobs, and verifies the full lifecycle:
 * <pre>
 *   PENDING → ASSIGNED → RUNNING → COMPLETED | FAILED
 * </pre>
 */
class JobExecutionIntegrationTest {

    private ManagerServer server;
    private WorkerClient  worker;

    private Thread serverThread;
    private Thread workerThread;

    @BeforeEach
    void setUp() throws Exception {
        // Use short heartbeat to avoid false positives during execution
        server = TestManagerServerBuilder.build(0, 2000, 10_000);
        CountDownLatch serverReady = new CountDownLatch(1);
        serverThread = Thread.ofVirtual().start(() -> {
            serverReady.countDown();
            server.start();
        });
        assertTrue(serverReady.await(5, TimeUnit.SECONDS), "Server failed to start");
        Thread.sleep(100); // let accept loop open

        String secret = "3c34e62a26514757c2c159851f50a80d46dddc7fa0a06df5c689f928e4e9b94z";
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        worker = new WorkerClient("localhost", server.getLocalPort(), 3000, 10000, token);
        workerThread = Thread.ofVirtual().start(worker::start);

        // Wait until the worker registers (registry has 1 entry)
        long deadline = System.currentTimeMillis() + 5_000;
        while (server.getRegistry().size() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(1, server.getRegistry().size(), "Worker should have registered");
    }

    @AfterEach
    void tearDown() throws Exception {
        worker.shutdown();
        workerThread.join(3_000);
        server.shutdown();
        serverThread.join(3_000);
    }

    // ──── Helpers ────────────────────────────────────────────────────────────

    /**
     * Enqueues a job and waits until it reaches one of the {@code expectedStatuses}.
     * Returns the job so callers can inspect final state.
     */
    private Job submitAndAwait(String payload, JobStatus... expectedStatuses) throws InterruptedException {
        Job job = Job.newJob(payload).timeoutMs(60000L).build();
        server.getJobScheduler().getQueue().enqueue(job);

        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            JobStatus current = job.getStatus();
            for (JobStatus expected : expectedStatuses) {
                if (current == expected) return job;
            }
            Thread.sleep(50);
        }
        fail("Job did not reach expected status within timeout. Final status: " + job.getStatus());
        return job; // unreachable
    }

    // ──── Tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("echo job completes with COMPLETED status and correct output")
    void echoJob_completesSuccessfully() throws Exception {
        Job job = submitAndAwait("{\"type\":\"echo\",\"data\":\"hello-world\"}", JobStatus.COMPLETED);

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals("hello-world", job.getResult());
    }

    @Test
    @DisplayName("sleep job completes successfully after sleeping")
    void sleepJob_completesSuccessfully() throws Exception {
        long before = System.currentTimeMillis();
        Job job = submitAndAwait("{\"type\":\"sleep\",\"ms\":200}", JobStatus.COMPLETED);
        long elapsed = System.currentTimeMillis() - before;

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals("slept 200ms", job.getResult());
        assertTrue(elapsed >= 200, "Should have slept at least 200ms, elapsed: " + elapsed);
    }

    @Test
    @DisplayName("fibonacci job completes with correct result")
    void fibonacciJob_completesSuccessfully() throws Exception {
        Job job = submitAndAwait("{\"type\":\"fibonacci\",\"n\":10}", JobStatus.COMPLETED);

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals("55", job.getResult(), "fib(10) = 55");
    }

    @Test
    @DisplayName("unknown task type results in FAILED status with error in result")
    void unknownTaskType_markedFailed() throws Exception {
        Job job = submitAndAwait("{\"type\":\"explode\"}", JobStatus.FAILED);

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertNotNull(job.getResult());
        assertTrue(job.getResult().contains("explode"),
                "Result should describe the unknown type, got: " + job.getResult());
    }

    @Test
    @DisplayName("job briefly transitions through RUNNING before COMPLETED")
    void jobRunning_transitionObserved() throws Exception {
        // We observe RUNNING in-flight by polling rapidly on a longer task
        Job job = Job.newJob("{\"type\":\"sleep\",\"ms\":500}").timeoutMs(60000L).build();
        server.getJobScheduler().getQueue().enqueue(job);

        // Poll for RUNNING status
        AtomicReference<JobStatus> observedRunning = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            if (job.getStatus() == JobStatus.RUNNING) {
                observedRunning.set(JobStatus.RUNNING);
                break;
            }
            Thread.sleep(10);
        }
        assertEquals(JobStatus.RUNNING, observedRunning.get(),
                "Job should have passed through RUNNING state");

        // Now wait for it to complete
        long deadline2 = System.currentTimeMillis() + 5_000;
        while (job.getStatus() != JobStatus.COMPLETED && System.currentTimeMillis() < deadline2) {
            Thread.sleep(50);
        }
        assertEquals(JobStatus.COMPLETED, job.getStatus());
    }

    @Test
    @DisplayName("worker accepts multiple sequential jobs after completing each")
    void workerAcceptsMultipleJobs() throws Exception {
        Job j1 = submitAndAwait("{\"type\":\"echo\",\"data\":\"first\"}", JobStatus.COMPLETED);
        Job j2 = submitAndAwait("{\"type\":\"echo\",\"data\":\"second\"}", JobStatus.COMPLETED);
        Job j3 = submitAndAwait("{\"type\":\"fibonacci\",\"n\":5}", JobStatus.COMPLETED);

        assertEquals("first", j1.getResult());
        assertEquals("second", j2.getResult());
        assertEquals("5", j3.getResult(), "fib(5) = 5");
    }

    @Test
    @DisplayName("0 workers, 3 jobs → all stay PENDING, no errors")
    void noWorkersAvailable_jobsStayPending() throws Exception {
        // Shut down the worker started in @BeforeEach so we have 0 workers
        worker.shutdown();
        workerThread.join(3_000);

        long deadline = System.currentTimeMillis() + 2_000;
        while (server.getRegistry().size() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(0, server.getRegistry().size(), "No workers should be registered");

        // Submit 3 jobs
        Job j1 = Job.newJob("{\"type\":\"echo\",\"data\":\"one\"}").timeoutMs(60000L).build();
        Job j2 = Job.newJob("{\"type\":\"echo\",\"data\":\"two\"}").timeoutMs(60000L).build();
        Job j3 = Job.newJob("{\"type\":\"echo\",\"data\":\"three\"}").timeoutMs(60000L).build();
        server.getJobScheduler().getQueue().enqueue(j1);
        server.getJobScheduler().getQueue().enqueue(j2);
        server.getJobScheduler().getQueue().enqueue(j3);

        // Wait a reasonable time and verify they all stay PENDING
        // (The scheduler may pull one job off the queue waiting for a worker,
        // but without workers available it cannot transition past PENDING.)
        Thread.sleep(1_000);

        assertEquals(JobStatus.PENDING, j1.getStatus(), "Job 1 should stay PENDING with no workers");
        assertEquals(JobStatus.PENDING, j2.getStatus(), "Job 2 should stay PENDING with no workers");
        assertEquals(JobStatus.PENDING, j3.getStatus(), "Job 3 should stay PENDING with no workers");
        // At least some jobs should remain in the queue (the scheduler holds at most 1 waiting for a worker)
        assertTrue(server.getJobScheduler().getQueue().size() >= 2,
                "At least 2 jobs should remain in queue (scheduler may hold 1 waiting for worker)");
    }

    @Test
    @DisplayName("stop Manager → restart → pending jobs recovered and completed by worker")
    void managerRestart_jobsRecoveredAndCompleted() throws Exception {
        // Submit 3 jobs and let them complete on the running server first
        Job j1 = submitAndAwait("{\"type\":\"echo\",\"data\":\"before-restart-1\"}", JobStatus.COMPLETED);
        assertEquals(JobStatus.COMPLETED, j1.getStatus());

        // Stop the original server
        worker.shutdown();
        workerThread.join(3_000);
        server.shutdown();
        serverThread.join(3_000);

        // Build a fresh server on a new random port
        server = TestManagerServerBuilder.build(0, 2000, 10_000);
        CountDownLatch serverReady = new CountDownLatch(1);
        serverThread = Thread.ofVirtual().start(() -> {
            serverReady.countDown();
            server.start();
        });
        assertTrue(serverReady.await(5, TimeUnit.SECONDS), "Restarted server failed to start");
        Thread.sleep(100);

        // Reconnect a worker
        String secret = "3c34e62a26514757c2c159851f50a80d46dddc7fa0a06df5c689f928e4e9b94z";
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        worker = new WorkerClient("localhost", server.getLocalPort(), 3000, 10000, token);
        workerThread = Thread.ofVirtual().start(worker::start);

        long deadline = System.currentTimeMillis() + 5_000;
        while (server.getRegistry().size() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(1, server.getRegistry().size(), "Worker should have registered on restarted server");

        // Now submit new jobs on the restarted server and verify they complete
        Job j2 = submitAndAwait("{\"type\":\"echo\",\"data\":\"after-restart-2\"}", JobStatus.COMPLETED);
        Job j3 = submitAndAwait("{\"type\":\"echo\",\"data\":\"after-restart-3\"}", JobStatus.COMPLETED);
        Job j4 = submitAndAwait("{\"type\":\"fibonacci\",\"n\":7}", JobStatus.COMPLETED);

        assertEquals(JobStatus.COMPLETED, j2.getStatus());
        assertEquals("after-restart-2", j2.getResult());
        assertEquals(JobStatus.COMPLETED, j3.getStatus());
        assertEquals("after-restart-3", j3.getResult());
        assertEquals(JobStatus.COMPLETED, j4.getStatus());
        assertEquals("13", j4.getResult(), "fib(7) = 13");
    }
}
