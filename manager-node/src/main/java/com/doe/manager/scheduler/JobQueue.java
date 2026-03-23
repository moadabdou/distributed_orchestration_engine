package com.doe.manager.scheduler;

import com.doe.core.model.Job;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Thread-safe FIFO job queue backed by a {@link ConcurrentLinkedDeque}.
 * <p>
 * The deque is used (rather than {@code ConcurrentLinkedQueue}) to support
 * efficient head-insertion when re-queuing a job that could not be assigned.
 */
public class JobQueue {

    private final ConcurrentLinkedDeque<Job> deque = new ConcurrentLinkedDeque<>();

    /**
     * Adds a job to the tail of the queue.
     *
     * @param job the job to enqueue; must not be null
     */
    public void enqueue(Job job) {
        if (job == null) throw new NullPointerException("job must not be null");
        deque.addLast(job);
    }

    /**
     * Removes and returns the job at the head of the queue, or {@code null}
     * if the queue is empty.
     */
    public Job dequeue() {
        return deque.pollFirst();
    }

    /**
     * Re-inserts a job at the <em>head</em> of the queue so it will be the
     * next job scheduled (used when no idle worker was available).
     *
     * @param job the job to requeue; must not be null
     */
    public void requeue(Job job) {
        if (job == null) throw new NullPointerException("job must not be null");
        deque.addFirst(job);
    }

    /** Returns the number of pending jobs in the queue. */
    public int size() {
        return deque.size();
    }

    /** Returns {@code true} if the queue contains no jobs. */
    public boolean isEmpty() {
        return deque.isEmpty();
    }
}
