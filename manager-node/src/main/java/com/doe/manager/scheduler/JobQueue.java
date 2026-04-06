package com.doe.manager.scheduler;

import com.doe.core.model.Job;
import com.doe.core.registry.JobRegistry;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Thread-safe FIFO job queue backed by a {@link ConcurrentLinkedDeque}.
 * <p>
 * The deque is used (rather than {@code ConcurrentLinkedQueue}) to support
 * efficient head-insertion when re-queuing a job that could not be assigned.
 */
@Component
public class JobQueue {

    private final ConcurrentLinkedDeque<Job> deque = new ConcurrentLinkedDeque<>();
    private final JobRegistry registry;
    private final int capacity;
    private final AtomicInteger sizeTracker = new AtomicInteger(0);

    /**
     * Creates a new JobQueue.
     *
     * @param registry the registry to register jobs with; may be null in tests
     * @param capacity the maximum capacity of the queue
     */
    @Autowired
    public JobQueue(JobRegistry registry, @Value("${job-queue.capacity:10000}") int capacity) {
        this.registry = registry;
        this.capacity = capacity;
    }

    /**
     * Alternative constructor for backward compatibility in tests.
     */
    public JobQueue(JobRegistry registry) {
        this(registry, 10000);
    }

    /**
     * Adds a job to the tail of the queue.
     *
     * @param job the job to enqueue; must not be null
     */
    public void enqueue(Job job) {
        if (job == null) throw new NullPointerException("job must not be null");
        if (sizeTracker.incrementAndGet() > capacity) {
            sizeTracker.decrementAndGet();
            throw new JobQueueFullException("JobQueue is at full capacity (" + capacity + ")");
        }
        if (registry != null) {
            registry.register(job);
        }
        deque.addLast(job);
    }

    /**
     * Removes and returns the job at the head of the queue, or {@code null}
     * if the queue is empty.
     */
    public Job dequeue() {
        Job job = deque.pollFirst();
        if (job != null) {
            sizeTracker.decrementAndGet();
        }
        return job;
    }

    /**
     * Re-inserts a job at the <em>head</em> of the queue so it will be the
     * next job scheduled (used when no idle worker was available).
     *
     * @param job the job to requeue; must not be null
     */
    public void requeue(Job job) {
        if (job == null) throw new NullPointerException("job must not be null");
        if (sizeTracker.incrementAndGet() > capacity) {
            sizeTracker.decrementAndGet();
            throw new JobQueueFullException("JobQueue is at full capacity (" + capacity + ")");
        }
        deque.addFirst(job);
    }

    /** Returns the number of pending jobs in the queue. */
    public int size() {
        return sizeTracker.get();
    }

    /** Returns {@code true} if the queue contains no jobs. */
    public boolean isEmpty() {
        return sizeTracker.get() <= 0;
    }
}
