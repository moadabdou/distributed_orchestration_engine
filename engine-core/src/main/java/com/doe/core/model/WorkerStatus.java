package com.doe.core.model;

/**
 * Runtime status of a connected worker node.
 * <p>
 * Transitions: {@code ONLINE}.
 */
public enum WorkerStatus {
    /** Worker is connected and available to accept new jobs within capacity limits. */
    ONLINE,
    /** Worker has disconnected or been declared dead. Persisted in DB only. */
    OFFLINE
}
