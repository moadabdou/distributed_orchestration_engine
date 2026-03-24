package com.doe.manager.server;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.worker.client.WorkerClient;
import com.doe.manager.scheduler.JobQueue;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end Chaos integration test for Issue #010 — Worker Crash Recovery.
 * <p>
 * Ensures that if a worker dies mid-execution, its active jobs are re-queued to
 * other workers and eventually reach COMPLETED.
 */
class CrashRecoveryIntegrationTest {

    private ManagerServer server;
    private final List<WorkerClient> workers = new ArrayList<>();
    private final List<Thread> workerThreads = new ArrayList<>();
    private Thread serverThread;

    @BeforeEach
    void setUp() throws Exception {
        // Fast heartbeat checks and monitor intervals to speed up tests, 
        // using port 0 for OS-assigned port.
        server = new ManagerServer(0, 1000, 3000);
        
        CountDownLatch serverReady = new CountDownLatch(1);
        serverThread = Thread.ofVirtual().start(() -> {
            try {
                serverReady.countDown();
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(serverReady.await(5, TimeUnit.SECONDS), "Server failed to start");
        Thread.sleep(100); // let accept loop open

        // Start 3 workers
        for (int i = 0; i < 3; i++) {
            WorkerClient worker = new WorkerClient("localhost", server.getLocalPort(), 1000);
            workers.add(worker);
            Thread workerThread = Thread.ofVirtual().start(worker::start);
            workerThreads.add(workerThread);
        }

        // Wait until all 3 register
        long deadline = System.currentTimeMillis() + 5_000;
        while (server.getRegistry().size() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(3, server.getRegistry().size(), "All 3 workers should have registered");
    }

    @AfterEach
    void tearDown() throws Exception {
        for (WorkerClient w : workers) {
            w.shutdown();
        }
        for (Thread t : workerThreads) {
            t.join(3_000);
        }
        server.shutdown();
        serverThread.join(3_000);
    }

    @Test
    @DisplayName("20 jobs complete even if one worker is killed mid-execution")
    void chaosRecoveryTest_WorkerKilled() throws Exception {
        List<Job> jobs = new ArrayList<>();
        JobQueue queue = server.getJobScheduler().getQueue();

        // Submit 20 long-ish running jobs (sleep 600ms)
        for (int i = 0; i < 20; i++) {
            Job job = Job.newJob("{\"type\":\"sleep\",\"ms\":600}")
                         .timeoutMs(5000) // Ensure that if the worker connection zombies, the timeout monitor will kick in.
                         .build();
            jobs.add(job);
            queue.enqueue(job);
        }

        // Wait a small amount so jobs start getting distributed
        Thread.sleep(1000);

        // Kill worker 0 abruptly
        WorkerClient doomedWorker = workers.get(0);
        doomedWorker.shutdown(); // This closes socket, simulating death

        // Await all jobs reaching COMPLETED
        long deadline = System.currentTimeMillis() + 30_000; // ample time for recovery and retries
        boolean allDone = false;
        while (System.currentTimeMillis() < deadline) {
            allDone = jobs.stream().allMatch(j -> j.getStatus() == JobStatus.COMPLETED);
            if (allDone) break;
            Thread.sleep(500);
        }

        // Output some stats
        long failedCount = jobs.stream().filter(j -> j.getStatus() == JobStatus.FAILED).count();
        int recoveredInstances = jobs.stream().mapToInt(Job::getRetryCount).sum();

        assertTrue(allDone, "Not all jobs completed! Failed count: " + failedCount + 
            ", PENDING/ASSIGNED/RUNNING: " + jobs.stream().filter(j -> j.getStatus() != JobStatus.COMPLETED).count());
        assertTrue(recoveredInstances > 0, "Expected at least one job to be recovered (retried)");
    }
    
    @Test
    @DisplayName("Job timeout monitor triggers when worker hangs/ignores job")
    void jobTimeoutTest() throws Exception {
        JobQueue queue = server.getJobScheduler().getQueue();

        // Submit a job that sleeps for a LONG time, but with a very short timeout.
        // Worker will start doing it, but timeout monitor should catch it before completion.
        Job job = Job.newJob("{\"type\":\"sleep\",\"ms\":10000}")
                     .timeoutMs(1000) // Expect it to timeout quickly
                     .build();
        queue.enqueue(job);
        
        // Ensure that eventually the job is marked as FAILED (since max retries = 3 means it'll retry 3 times and then fail)
        // Each retry runs for ~1s + buffer (5s default buffer in monitor). Total time ~18-24s.
        // Or wait, if we drop the worker, it's easier. The test just expects FAILED eventually.
        
        long deadline = System.currentTimeMillis() + 45_000; // 3 retries * (10s + interval)
        while (System.currentTimeMillis() < deadline) {
            if (job.getStatus() == JobStatus.FAILED) break;
            Thread.sleep(1000);
        }
        
        assertEquals(JobStatus.FAILED, job.getStatus(), "Job should have failed after exhausting retries due to timeouts");
    }
}
