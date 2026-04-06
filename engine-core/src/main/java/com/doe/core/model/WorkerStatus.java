package com.doe.core.model;

/**
 * Runtime status of a connected worker node.
 * <p>
 * Transitions: {@code IDLE ↔ BUSY}.
 */
public enum WorkerStatus {
    /** Worker is connected and available to accept new jobs. */
    IDLE,
    /** Worker is currently executing a job. */
    BUSY,
    /** Worker has disconnected or been declared dead. Persisted in DB only. */
    OFFLINE
}
