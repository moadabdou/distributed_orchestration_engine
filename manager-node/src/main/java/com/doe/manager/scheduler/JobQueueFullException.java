package com.doe.manager.scheduler;

/**
 * Exception thrown when the job queue is at maximum capacity and cannot
 * accept any additional jobs.
 */
public class JobQueueFullException extends RuntimeException {
    public JobQueueFullException(String message) {
        super(message);
    }
}
