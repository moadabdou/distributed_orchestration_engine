package com.doe.core.registry;

import com.doe.core.model.WorkerConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WorkerRegistryTest {

    private WorkerRegistry registry;
    private Socket stubSocket;

    @BeforeEach
    void setUp() {
        registry = new WorkerRegistry();
        stubSocket = new Socket(); // dummy
    }

    @Test
    void register_addsToAvailableQueue() throws InterruptedException {
        WorkerConnection worker = new WorkerConnection(UUID.randomUUID(), stubSocket);
        registry.register(worker);

        UUID testJobId = UUID.randomUUID();
        WorkerConnection found = registry.findAvailableWorker(testJobId);
        
        assertEquals(worker, found);
        assertEquals(1, found.getActiveJobCount());
        assertTrue(found.getActiveJobs().contains(testJobId));
    }

    @Test
    void findAvailableWorker_blocksUntilAvailable() throws InterruptedException {
        CountDownLatch acquired = new CountDownLatch(1);
        AtomicReference<WorkerConnection> acquiredWorker = new AtomicReference<>();

        Thread t = new Thread(() -> {
            try {
                acquiredWorker.set(registry.findAvailableWorker(UUID.randomUUID()));
                acquired.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.start();

        // Give thread time to block
        assertFalse(acquired.await(100, TimeUnit.MILLISECONDS), "Thread should block if queue is empty");

        WorkerConnection worker = new WorkerConnection(UUID.randomUUID(), stubSocket);
        registry.register(worker);

        assertTrue(acquired.await(2, TimeUnit.SECONDS), "Thread should unblock after worker is registered");
        assertNotNull(acquiredWorker.get());
        assertEquals(1, acquiredWorker.get().getActiveJobCount());
    }

    @Test
    void releaseCapacity_makesWorkerAvailableAgain() throws InterruptedException {
        WorkerConnection worker = new WorkerConnection(UUID.randomUUID(), stubSocket);
        registry.register(worker);

        // Max it out
        for (int i = 0; i < worker.getMaxCapacity(); i++) {
            UUID j = UUID.randomUUID();
            WorkerConnection found = registry.findAvailableWorker(j);
            assertEquals(worker, found);
        }
        
        assertEquals(worker.getMaxCapacity(), worker.getActiveJobCount());

        // Should block if we try to find another
        AtomicReference<WorkerConnection> additional = new AtomicReference<>();
        CountDownLatch additionalLatch = new CountDownLatch(1);
        UUID overflowJob = UUID.randomUUID();
        
        Thread t = new Thread(() -> {
            try {
                additional.set(registry.findAvailableWorker(overflowJob));
                additionalLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.start();
        
        assertFalse(additionalLatch.await(100, TimeUnit.MILLISECONDS));
        
        // Release 1 job
        UUID jobToRelease = worker.getActiveJobs().iterator().next();
        registry.releaseCapacity(worker.getId(), jobToRelease);
        
        assertTrue(additionalLatch.await(2, TimeUnit.SECONDS));
        assertNotNull(additional.get());
        assertTrue(worker.getActiveJobs().contains(overflowJob));
    }

    @Test
    void findAvailableWorker_concurrentLastSlotReservation() throws InterruptedException {
        WorkerConnection worker = new WorkerConnection(UUID.randomUUID(), stubSocket);
        registry.register(worker);
        
        // consume all but 1 capacity
        for (int i = 0; i < worker.getMaxCapacity() - 1; i++) {
            registry.findAvailableWorker(UUID.randomUUID());
        }
        
        assertEquals(worker.getMaxCapacity() - 1, worker.getActiveJobCount());

        int numThreads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        
        AtomicReference<WorkerConnection> successfulAcquisition = new AtomicReference<>(null);

        for (int i = 0; i < numThreads; i++) {
            UUID testJobId = UUID.randomUUID();
            new Thread(() -> {
                try {
                    startLatch.await();
                    // One thread will get the last slot, the others will block indefinitely on this call 
                    // until more capacity is released or another worker added. But since we use interrupt below, it's fine.
                    WorkerConnection w = registry.findAvailableWorker(testJobId);
                    if (w != null) {
                        successfulAcquisition.set(w);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        
        // give threads chance to compete
        Thread.sleep(100); 
        
        assertEquals(worker.getMaxCapacity(), worker.getActiveJobCount());
        assertNotNull(successfulAcquisition.get(), "At least one thread should have acquired the last slot");
        
        // Interrupt waiting threads to let doneLatch complete
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getState() == Thread.State.WAITING || t.getState() == Thread.State.TIMED_WAITING) {
                // simple interrupt strategy for the test
                t.interrupt();
            }
        }
    }

    @Test
    void releaseCapacity_rogueReleaseDoesNotCauseErrors() throws InterruptedException {
        // Test double release or releasing unknown job
        WorkerConnection worker = new WorkerConnection(UUID.randomUUID(), stubSocket);
        registry.register(worker);
        
        UUID jobId = UUID.randomUUID();
        registry.findAvailableWorker(jobId);
        assertEquals(1, worker.getActiveJobCount());

        // First valid release
        registry.releaseCapacity(worker.getId(), jobId);
        assertEquals(0, worker.getActiveJobCount());

        // Rogue: Double release
        registry.releaseCapacity(worker.getId(), jobId);
        assertEquals(0, worker.getActiveJobCount());

        // Rogue: Unknown job
        registry.releaseCapacity(worker.getId(), UUID.randomUUID());
        assertEquals(0, worker.getActiveJobCount());
        
        // Rogue: Unknown worker
        registry.releaseCapacity(UUID.randomUUID(), UUID.randomUUID());
    }

}
