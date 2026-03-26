package com.doe.manager.server;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.worker.client.WorkerClient;
import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

        worker = new WorkerClient("localhost", server.getLocalPort(), 3000);
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
        Job job = Job.newJob(payload).build();
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
        Job job = Job.newJob("{\"type\":\"sleep\",\"ms\":500}").build();
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
}
