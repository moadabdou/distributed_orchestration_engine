package com.doe.core.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Observer interface for engine state-change events.
 * <p>
 * Implementations (e.g. {@code DatabaseEventListener}) receive structured callbacks
 * whenever the engine mutates worker or job state. There is no Spring dependency here
 * so this interface can be referenced by both {@code engine-core} and any downstream module.
 * <p>
 * All method arguments are immutable value-snapshots taken at the moment of the state change
 * to avoid aliasing issues across threads.
 */
public interface EngineEventListener {

    // ─── Worker events ───────────────────────────────────────────────────────

    /**
     * Fired when a new worker successfully registers over TCP.
     *
     * @param workerId     manager-assigned UUID for this worker
     * @param hostname     hostname extracted from the REGISTER_WORKER payload
     * @param ipAddress    remote IP of the worker socket
     * @param maxCapacity  the maximum concurrent capacity for this worker
     * @param registeredAt moment of registration (from the domain object)
     */
    void onWorkerRegistered(UUID workerId, String hostname, String ipAddress, int maxCapacity, Instant registeredAt);

    /**
     * Fired on every heartbeat received from a worker.
     * Implementations are expected to buffer these writes to avoid DB write amplification.
     *
     * @param workerId  the worker that sent the heartbeat
     * @param timestamp the timestamp recorded by the engine
     */
    void onWorkerHeartbeat(UUID workerId, Instant timestamp);

    /**
     * Fired when a worker is declared dead — either by the heartbeat monitor
     * (timeout) or by a TCP disconnect detected in the handler thread.
     *
     * @param workerId the worker that died
     */
    void onWorkerDied(UUID workerId);

    // ─── Job events ──────────────────────────────────────────────────────────

    /**
     * Fired when a job transitions PENDING → ASSIGNED.
     * The socket write to the worker has already succeeded at this point.
     *
     * @param jobId      the assigned job
     * @param workerId   the worker it was assigned to
     * @param updatedAt  the timestamp recorded in the domain object
     */
    void onJobAssigned(UUID jobId, UUID workerId, Instant updatedAt);

    /**
     * Fired when the manager receives a {@code JOB_RUNNING} message, transitioning
     * the job ASSIGNED → RUNNING.
     *
     * @param jobId     the job now running
     * @param updatedAt the timestamp recorded in the domain object
     */
    void onJobRunning(UUID jobId, Instant updatedAt);

    /**
     * Fired when a job transitions to COMPLETED after receiving a successful JOB_RESULT.
     *
     * @param jobId     the completed job
     * @param workerId  the worker that executed this job (may be null if job was never assigned)
     * @param summary   the output string (summary) from the worker
     * @param updatedAt the timestamp recorded in the domain object
     */
    void onJobCompleted(UUID jobId, UUID workerId, String summary, Instant updatedAt);

    /**
     * Fired when a job transitions to FAILED (worker reported failure, max retries
     * exceeded, or permanent timeout).
     *
     * @param jobId     the failed job
     * @param workerId  the worker that executed this job (may be null if job was never assigned)
     * @param summary   failure reason / worker output summary
     * @param updatedAt the timestamp recorded in the domain object
     */
    void onJobFailed(UUID jobId, UUID workerId, String summary, Instant updatedAt);

    /**
     * Fired when a job transitions to CANCELLED.
     *
     * @param jobId     the cancelled job
     * @param workerId  the worker that was assigned (may be null if job was never assigned)
     * @param summary   reason for cancellation summary
     * @param updatedAt the timestamp recorded in the domain object
     */
    void onJobCancelled(UUID jobId, UUID workerId, String summary, Instant updatedAt);

    /**
     * Fired whenever a job is re-inserted as PENDING — either by crash-recovery
     * (worker death) or by the timeout monitor (job exceeded its execution timeout).
     *
     * @param jobId      the job being re-queued
     * @param retryCount the new retry count (already incremented)
     * @param updatedAt  the timestamp recorded in the domain object
     */
    void onJobRequeued(UUID jobId, int retryCount, Instant updatedAt);

    /**
     * Fired when a job is marked as SKIPPED because it was PENDING when the workflow terminated.
     *
     * @param jobId     the skipped job
     * @param updatedAt the timestamp recorded in the domain object
     */
    void onJobSkipped(UUID jobId, Instant updatedAt);
}
