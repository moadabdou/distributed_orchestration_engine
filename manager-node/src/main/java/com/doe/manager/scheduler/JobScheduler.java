package com.doe.manager.scheduler;

import com.doe.core.event.EngineEventListener;
import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkerConnection;
import com.doe.core.protocol.MessageType;
import com.doe.core.protocol.ProtocolEncoder;
import com.doe.core.registry.WorkerRegistry;
import com.doe.manager.security.JwtService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.stereotype.Component;

/**
 * Continuously dequeues pending jobs and assigns them to idle workers.
 * <p>
 * Runs on a single Java-21 Virtual Thread.
 * {@link WorkerRegistry#findIdle()} <em>blocks</em> until a worker is available,
 * so the loop only sleeps when the job queue is empty — no busy-waiting.
 *
 * <pre>
 * while (running) {
 *     job = queue.dequeue();
 *     if (job == null)  → sleep, continue
 *     worker = registry.findIdle();   // blocks until a worker is free
 *     job.transition(ASSIGNED)
 *     send ASSIGN_JOB over socket
 * }
 * </pre>
 *
 * <p>Fires the following {@link EngineEventListener} events:
 * <ul>
 *   <li>{@code onWorkerBusy} — after the job is successfully sent to the worker</li>
 *   <li>{@code onJobAssigned} — after the job is successfully sent to the worker</li>
 *   <li>{@code onWorkerIdle} / {@code onJobRequeued} — on socket-write failure (rollback)</li>
 * </ul>
 */
@Component
public class JobScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(JobScheduler.class);
    private static final long POLL_SLEEP_MS = 100;
    private static final Gson GSON = new Gson();

    private final JobQueue queue;
    private final WorkerRegistry registry;
    private final EngineEventListener eventListener;
    private final JwtService jwtService;
    private volatile boolean running;
    private Thread schedulerThread;

    public JobScheduler(JobQueue queue, WorkerRegistry registry, EngineEventListener eventListener, JwtService jwtService) {
        this.queue    = queue;
        this.registry = registry;
        this.eventListener = eventListener;
        this.jwtService = jwtService;
    }

    /**
     * Starts the scheduler loop on a new virtual thread.
     * No-op if already started.
     */
    public synchronized void start() {
        if (schedulerThread != null && schedulerThread.isAlive()) {
            return;
        }
        running = true;
        schedulerThread = Thread.ofVirtual()
                .name("job-scheduler")
                .start(this::schedulerLoop);
        LOG.info("JobScheduler started");
    }

    /**
     * Signals the scheduler to stop and waits for the thread to terminate.
     */
    public synchronized void stop() {
        running = false;
        if (schedulerThread != null) {
            schedulerThread.interrupt();
        }
        LOG.info("JobScheduler stopped");
    }

    // ──── Scheduler loop ─────────────────────────────────────────────────────

    private void schedulerLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Job job = queue.dequeue();

                if (job == null) {
                    // Nothing to process — block until a new job is submitted
                    Thread.sleep(POLL_SLEEP_MS);
                    continue;
                }

                if (job.getStatus() == com.doe.core.model.JobStatus.CANCELLED) {
                    LOG.info("Job {} was cancelled before assignment, dropping from queue", job.getId());
                    continue;
                }

                // Blocks until an IDLE worker is available — no busy-waiting
                WorkerConnection worker = registry.findAvailableWorker(job.getId());

                assignJob(job, worker);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.debug("Scheduler thread interrupted, stopping");
            } catch (Exception e) {
                LOG.error("Unexpected error in scheduling loop, attempting to recover", e);
            }
        }
    }

    /**
     * Transitions the job to ASSIGNED, marks the worker BUSY, and writes
     * an {@code ASSIGN_JOB} message to the worker's socket.
     * <p>
     * On failure (socket error) the job is rolled back to {@link JobStatus#PENDING},
     * its {@code assignedWorkerId} is cleared, and the worker is freed (IDLE) so
     * both can be reused on the next scheduling cycle.
     * <p>
     * DB sync events fired here:
     * <ul>
     *   <li>Success → {@code onWorkerBusy}, {@code onJobAssigned}</li>
     *   <li>Rollback → {@code onWorkerIdle}, {@code onJobRequeued}</li>
     * </ul>
     */
    private void assignJob(Job job, WorkerConnection worker) {
        MDC.put("jobId", job.getId().toString());
        try {
            // Advance job state machine: PENDING → ASSIGNED
            job.transition(JobStatus.ASSIGNED);
            job.setAssignedWorkerId(worker.getId());

            LOG.info("Assigning job to worker {} | queue size remaining: {}",
                    worker.getId(), queue.size());

            try {
                // Record the reverse mapping: worker → job (for JOB_RESULT correlation)
                // MUST happen before sending to avoid race conditions with fast workers

                // Notify DB that the worker is now handling another job and the job has been ASSIGNED
                // MUST happen before sending to avoid out-of-order COMPLETED → ASSIGNED transitions
                eventListener.onJobAssigned(job.getId(), worker.getId(), job.getUpdatedAt());

                // Build the ASSIGN_JOB envelope: { "jobId": "...", "payload": <original payload> }
                // so the worker can echo jobId back in JOB_RUNNING and JOB_RESULT.
                JsonObject envelope = new JsonObject();
                envelope.addProperty("jobId", job.getId().toString());
                if (job.getWorkflowId() != null) {
                    envelope.addProperty("workflowId", job.getWorkflowId().toString());
                }
                envelope.addProperty("timeoutMs", job.getTimeoutMs());
                
                // Generate and include job token for event system authentication
                String jobToken = jwtService.generateJobToken(job.getWorkflowId(), job.getId());
                envelope.addProperty("job_token", jobToken);

                // Embed the original payload as a nested JSON element (not a double-encoded string)
                envelope.add("payload", JsonParser.parseString(job.getPayload()));

                byte[] wire = ProtocolEncoder.encode(MessageType.ASSIGN_JOB, GSON.toJson(envelope));
                OutputStream out = worker.getSocket().getOutputStream();
                synchronized (out) {          // guard concurrent writes on the same socket
                    out.write(wire);
                    out.flush();
                }

            } catch (IOException e) {
                LOG.error("Failed to send ASSIGN_JOB to worker {}. Rolling back job to PENDING.",
                        worker.getId(), e);
                // Roll back job state: ASSIGNED → PENDING (valid transition)
                job.transition(JobStatus.PENDING);
                job.setAssignedWorkerId(null);

                // Notify DB about the rollback
                eventListener.onJobRequeued(job.getId(), job.getRetryCount(), job.getUpdatedAt());

                // releaseCapacity() frees slot and re-offers worker if available
                registry.releaseCapacity(worker.getId(), job.getId());
                // Re-insert at the head of the queue so it is retried promptly
                queue.requeue(job);
            }
        } finally {
            MDC.remove("jobId");
        }
    }

    /** Returns {@code true} if the scheduler thread is alive. */
    public boolean isRunning() {
        return schedulerThread != null && schedulerThread.isAlive();
    }

    /** Exposes the queue for testing/monitoring purposes. */
    public JobQueue getQueue() {
        return queue;
    }
}
