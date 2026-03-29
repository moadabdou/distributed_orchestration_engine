package com.doe.manager.scheduler;

import com.doe.core.event.EngineEventListener;
import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkerConnection;
import com.doe.core.protocol.MessageType;
import com.doe.core.protocol.ProtocolEncoder;
import com.doe.core.registry.WorkerRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.stereotype.Component;

/**
 * Continuously polls the {@link JobQueue} and assigns pending jobs to idle workers.
 * <p>
 * Runs on a single Java-21 Virtual Thread. The loop is designed to be
 * responsive: it sleeps at most {@value #POLL_SLEEP_MS} ms when no work
 * or no workers are available.
 *
 * <pre>
 * while (running) {
 *     job = queue.dequeue();
 *     if (job == null)  → sleep, continue
 *     worker = registry.findIdle();
 *     if (worker == null) → requeue(job), sleep, continue
 *     job.transition(ASSIGNED)
 *     worker.trySetBusy()   ← already done inside findIdle()
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
    private volatile boolean running;
    private Thread schedulerThread;

    public JobScheduler(JobQueue queue, WorkerRegistry registry, EngineEventListener eventListener) {
        this.queue    = queue;
        this.registry = registry;
        this.eventListener = eventListener;
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
                    // Nothing to process — wait for new submissions
                    Thread.sleep(POLL_SLEEP_MS);
                    continue;
                }

                WorkerConnection worker = registry.findIdle();

                if (worker == null) {
                    // No idle worker — put job back at the head of the queue
                    queue.requeue(job);
                    LOG.debug("No idle workers available. Job {} requeued. Queue size: {}",
                            job.getId(), queue.size());
                    Thread.sleep(POLL_SLEEP_MS);
                    continue;
                }

                assignJob(job, worker);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.debug("Scheduler thread interrupted, stopping");
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
        // Advance job state machine: PENDING → ASSIGNED
        job.transition(JobStatus.ASSIGNED);
        job.setAssignedWorkerId(worker.getId());

        LOG.info("Assigning job {} to worker {} | queue size remaining: {}",
                job.getId(), worker.getId(), queue.size());

        try {
            // Record the reverse mapping: worker → job (for JOB_RESULT correlation)
            // MUST happen before sending to avoid race conditions with fast workers
            worker.setCurrentJob(job);

            // Notify DB that the worker is now BUSY and the job has been ASSIGNED
            // MUST happen before sending to avoid out-of-order COMPLETED → ASSIGNED transitions
            eventListener.onWorkerBusy(worker.getId());
            eventListener.onJobAssigned(job.getId(), worker.getId(), job.getUpdatedAt());

            // Build the ASSIGN_JOB envelope: { "jobId": "...", "payload": <original payload> }
            // so the worker can echo jobId back in JOB_RUNNING and JOB_RESULT.
            JsonObject envelope = new JsonObject();
            envelope.addProperty("jobId", job.getId().toString());
            envelope.addProperty("timeoutMs", job.getTimeoutMs());
            // Embed the original payload as a nested JSON element (not a double-encoded string)
            envelope.add("payload", JsonParser.parseString(job.getPayload()));

            byte[] wire = ProtocolEncoder.encode(MessageType.ASSIGN_JOB, GSON.toJson(envelope));
            OutputStream out = worker.getSocket().getOutputStream();
            synchronized (out) {          // guard concurrent writes on the same socket
                out.write(wire);
                out.flush();
            }

        } catch (IOException e) {
            LOG.error("Failed to send ASSIGN_JOB to worker {}. Rolling back job {} to PENDING.",
                    worker.getId(), job.getId(), e);
            // Roll back job state: ASSIGNED → PENDING (valid transition)
            job.transition(JobStatus.PENDING);
            job.setAssignedWorkerId(null);
            // setIdle() also clears currentJob atomically
            worker.setIdle();
            // Re-insert at the head of the queue so it is retried promptly
            queue.requeue(job);

            // Notify DB about the rollback
            eventListener.onWorkerIdle(worker.getId());
            eventListener.onJobRequeued(job.getId(), job.getRetryCount(), job.getUpdatedAt());
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
