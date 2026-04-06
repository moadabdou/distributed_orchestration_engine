package com.doe.core.registry;

import com.doe.core.model.Job;
import com.doe.core.model.WorkerConnection;
import com.doe.core.model.WorkerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WorkerRegistryTest {

    private WorkerRegistry registry;
    private final Socket stubSocket = new Socket(); // dummy unconnected socket

    @BeforeEach
    void setUp() {
        registry = new WorkerRegistry();
    }

    @Test
    void register_addsToIdleQueue() throws InterruptedException {
        WorkerConnection worker = new WorkerConnection(UUID.randomUUID(), stubSocket);
        registry.register(worker);

        // findIdle should return immediately without blocking since it's in the queue
        WorkerConnection found = registry.findIdle();
        
        assertNotNull(found, "Should find the registered worker");
        assertEquals(worker.getId(), found.getId(), "Should be the same worker");
        assertEquals(WorkerStatus.BUSY, found.getStatus(), "Worker should be marked BUSY by findIdle");
    }

    @Test
    void findIdle_blocksUntilWorkerAvailable_and_markIdle_wakesThread() throws InterruptedException {
        WorkerConnection worker = new WorkerConnection(UUID.randomUUID(), stubSocket);
        registry.register(worker);
        
        // Take the only worker so the queue is empty
        registry.findIdle();

        CountDownLatch blockingStarted = new CountDownLatch(1);
        CountDownLatch workerAcquired = new CountDownLatch(1);
        AtomicReference<WorkerConnection> acquiredWorker = new AtomicReference<>();

        Thread schedulerThread = new Thread(() -> {
            try {
                blockingStarted.countDown(); // signal test thread that we are about to block
                acquiredWorker.set(registry.findIdle());
                workerAcquired.countDown(); // signal test thread that we woke up
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        schedulerThread.start();

        // Wait to ensure the thread is blocking in findIdle()
        assertTrue(blockingStarted.await(1, TimeUnit.SECONDS));
        
        // At this point workerAcquired latch is still 1, meaning the thread is blocked.
        // Re-offer the worker by calling markIdle
        registry.markIdle(worker.getId());

        // Now the thread should unblock almost immediately
        assertTrue(workerAcquired.await(2, TimeUnit.SECONDS), "Thread should unblock after markIdle");
        assertNotNull(acquiredWorker.get());
        assertEquals(worker.getId(), acquiredWorker.get().getId());
    }

    @Test
    void unregister_drainsStaleDuringFindIdle() throws InterruptedException {
        WorkerConnection worker1 = new WorkerConnection(UUID.randomUUID(), stubSocket);
        WorkerConnection worker2 = new WorkerConnection(UUID.randomUUID(), stubSocket);
        
        // Both enter the idle queue
        registry.register(worker1);
        registry.register(worker2);

        // Disconnect worker1
        registry.unregister(worker1.getId());

        // findIdle should discard the stale worker1 and return worker2
        WorkerConnection found = registry.findIdle();
        
        assertEquals(worker2.getId(), found.getId(), "Should skip unregistered worker");
    }

    @Test
    void doubleOffer_casGuardCatchesBug() throws InterruptedException {
        WorkerConnection worker = new WorkerConnection(UUID.randomUUID(), stubSocket);
        registry.register(worker);

        // Get the worker, marking it BUSY
        WorkerConnection firstFind = registry.findIdle();
        assertEquals(WorkerStatus.BUSY, firstFind.getStatus());

        // Simulate a bug where the idle queue is bypassed and the item is re-offered manually 
        // without setting the status back to IDLE.
        // Wait, markIdle DOES set it to IDLE. To simulate a double-offer bug, we simulate
        // calling markIdle twice.
        registry.markIdle(worker.getId());
        
        // Take it out again to make it BUSY
        WorkerConnection secondFind = registry.findIdle();
        assertEquals(WorkerStatus.BUSY, secondFind.getStatus());
        
        // It's BUSY now. What if markIdle is called, then another thread calls it before
        // the first thread actually takes it?
        registry.markIdle(worker.getId());
        
        // Now it's IDLE and in queue.
        // Let's manually set it to BUSY to bypass CAS logic externally, simulating
        // the double-offer bug condition where take() gets a BUSY worker.
        WorkerConnection secondWorker = new WorkerConnection(UUID.randomUUID(), stubSocket);
        registry.register(secondWorker);
        
        // To test the exact CAS branch:
        // Worker 1 is in queue, but we make it BUSY.
        worker.trySetBusy();
        
        // findIdle() pulls worker1, fails CAS, continues and pulls worker2
        WorkerConnection found = registry.findIdle();
        
        assertEquals(secondWorker.getId(), found.getId());
    }

    @Test
    void concurrentFindIdle_noDuplicateAssignments() throws InterruptedException {
        int workerCount = 10;
        int threadCount = 10;
        
        for (int i = 0; i < workerCount; i++) {
            registry.register(new WorkerConnection(UUID.randomUUID(), stubSocket));
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(threadCount);
        
        AtomicInteger totalAssigned = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                try {
                    startLatch.await(); // wait for all threads to be ready
                    WorkerConnection w = registry.findIdle();
                    if (w != null) {
                        assertEquals(WorkerStatus.BUSY, w.getStatus(), "Worker must be BUSY");
                        totalAssigned.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            });
            t.start();
        }

        // Unleash all threads at once
        startLatch.countDown();
        
        assertTrue(allDone.await(5, TimeUnit.SECONDS), "All threads should complete");
        assertEquals(workerCount, totalAssigned.get(), "Each worker should be assigned exactly once");

        // Registry queue should be empty, next call should block (verifying with a short timeout via interrupted exception)
        Thread testBlocking = new Thread(() -> {
            try {
                registry.findIdle();
            } catch (InterruptedException e) {
                // Expected
            }
        });
        testBlocking.start();
        testBlocking.join(100);
        assertTrue(testBlocking.isAlive(), "Queue should be empty and block");
        testBlocking.interrupt();
    }
}
