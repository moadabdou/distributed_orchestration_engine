package com.doe.manager.metrics;

import com.doe.core.event.EngineEventListener;
import com.doe.manager.scheduler.JobQueue;
import com.doe.core.registry.WorkerRegistry;
import com.doe.core.registry.JobRegistry;
import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkerConnection;

import io.micrometer.core.instrument.*;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Bridges engine events to Micrometer metrics for Prometheus/Grafana monitoring.
 * <p>
 * Implements {@link EngineEventListener} so every state change in the engine
 * automatically updates the corresponding metric. Also registers gauges that
 * sample live in-memory state (queue depth, worker counts) on demand.
 *
 * <h3>Metrics exposed</h3>
 * <table>
 *   <tr><th>Metric name</th><th>Type</th><th>Description</th></tr>
 *   <tr><td>orchestration.jobs.submitted</td><td>Counter</td><td>Total jobs submitted via API</td></tr>
 *   <tr><td>orchestration.jobs.completed</td><td>Counter</td><td>Total jobs completed successfully</td></tr>
 *   <tr><td>orchestration.jobs.failed</td><td>Counter</td><td>Total jobs failed (after retries exhausted)</td></tr>
 *   <tr><td>orchestration.jobs.cancelled</td><td>Counter</td><td>Total jobs cancelled</td></tr>
 *   <tr><td>orchestration.jobs.assigned</td><td>Counter</td><td>Total jobs assigned to workers</td></tr>
 *   <tr><td>orchestration.jobs.requeued</td><td>Counter</td><td>Total jobs re-queued due to crash or timeout</td></tr>
 *   <tr><td>orchestration.jobs.retries</td><td>Counter</td><td>Total retry attempts across all jobs</td></tr>
 *   <tr><td>orchestration.jobs.duration</td><td>Timer</td><td>Job execution duration distribution (ms)</td></tr>
 *   <tr><td>orchestration.jobs.queue.pending</td><td>Gauge</td><td>Current pending queue depth</td></tr>
 *   <tr><td>orchestration.jobs.queue.assigned</td><td>Gauge</td><td>Jobs currently in ASSIGNED state</td></tr>
 *   <tr><td>orchestration.jobs.queue.running</td><td>Gauge</td><td>Jobs currently in RUNNING state</td></tr>
 *   <tr><td>orchestration.workers.active</td><td>Gauge</td><td>Currently connected (ONLINE) workers</td></tr>
 *   <tr><td>orchestration.workers.capacity.total</td><td>Gauge</td><td>Total job capacity across all workers</td></tr>
 *   <tr><td>orchestration.workers.capacity.used</td><td>Gauge</td><td>Currently used capacity (active job count)</td></tr>
 * </table>
 */
@Component
public class MetricsService implements EngineEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsService.class);
    private static final String NAMESPACE = "orchestration";

    private final MeterRegistry registry;
    private final JobQueue jobQueue;
    private final WorkerRegistry workerRegistry;
    private final JobRegistry jobRegistry;

    // Counters
    private Counter jobsSubmittedCounter;
    private Counter jobsCompletedCounter;
    private Counter jobsFailedCounter;
    private Counter jobsCancelledCounter;
    private Counter jobsAssignedCounter;
    private Counter jobsRequeuedCounter;
    private Counter jobRetriesCounter;

    // Timer — tracks job execution duration
    private Timer jobDurationTimer;

    /**
     * Tracks the timestamp when each job started running (ASSIGNED → RUNNING),
     * used to measure duration until COMPLETED/FAILED.
     */
    private final java.util.concurrent.ConcurrentHashMap<UUID, Instant> jobStartTimes =
            new java.util.concurrent.ConcurrentHashMap<>();

    public MetricsService(MeterRegistry registry,
                          JobQueue jobQueue,
                          WorkerRegistry workerRegistry,
                          JobRegistry jobRegistry) {
        this.registry = registry;
        this.jobQueue = jobQueue;
        this.workerRegistry = workerRegistry;
        this.jobRegistry = jobRegistry;
    }

    @PostConstruct
    void init() {
        // ─── Counters ────────────────────────────────────────────────────────
        jobsSubmittedCounter = Counter.builder(NAMESPACE + ".jobs.submitted")
                .description("Total jobs submitted via API")
                .register(registry);

        jobsCompletedCounter = Counter.builder(NAMESPACE + ".jobs.completed")
                .description("Total jobs completed successfully")
                .register(registry);

        jobsFailedCounter = Counter.builder(NAMESPACE + ".jobs.failed")
                .description("Total jobs failed after retries exhausted")
                .register(registry);

        jobsCancelledCounter = Counter.builder(NAMESPACE + ".jobs.cancelled")
                .description("Total jobs cancelled")
                .register(registry);

        jobsAssignedCounter = Counter.builder(NAMESPACE + ".jobs.assigned")
                .description("Total jobs assigned to workers")
                .register(registry);

        jobsRequeuedCounter = Counter.builder(NAMESPACE + ".jobs.requeued")
                .description("Total jobs re-queued due to crash or timeout")
                .register(registry);

        jobRetriesCounter = Counter.builder(NAMESPACE + ".jobs.retries")
                .description("Total retry attempts across all jobs")
                .register(registry);

        // ─── Timer ───────────────────────────────────────────────────────────
        jobDurationTimer = Timer.builder(NAMESPACE + ".jobs.duration")
                .description("Job execution duration distribution")
                .register(registry);

        // ─── Gauges — sampled live from in-memory state ──────────────────────

        // Queue depth (PENDING jobs waiting for a worker)
        Gauge.builder(NAMESPACE + ".jobs.queue.pending", jobQueue, JobQueue::size)
                .description("Current pending queue depth")
                .register(registry);

        // Active workers (ONLINE count from registry)
        Gauge.builder(NAMESPACE + ".workers.active", workerRegistry, r -> r.getAll().size())
                .description("Currently connected workers")
                .register(registry);

        // Total worker capacity
        Gauge.builder(NAMESPACE + ".workers.capacity.total", this, MetricsService::totalWorkerCapacity)
                .description("Total job capacity across all workers")
                .register(registry);

        // Used worker capacity
        Gauge.builder(NAMESPACE + ".workers.capacity.used", this, MetricsService::usedWorkerCapacity)
                .description("Currently used capacity (active job count)")
                .register(registry);

        // Jobs in ASSIGNED state
        Gauge.builder(NAMESPACE + ".jobs.queue.assigned", this, MetricsService::countJobsInStatusAssigned)
                .description("Jobs currently in ASSIGNED state")
                .register(registry);

        // Jobs in RUNNING state
        Gauge.builder(NAMESPACE + ".jobs.queue.running", this, MetricsService::countJobsInStatusRunning)
                .description("Jobs currently in RUNNING state")
                .register(registry);

        LOG.info("MetricsService initialized with {} custom metrics", 12);
    }

    // ─── EngineEventListener callbacks ────────────────────────────────────────

    @Override
    public void onWorkerRegistered(UUID workerId, String hostname, String ipAddress, int maxCapacity, Instant registeredAt) {
        // Gauge samples this live — no counter needed
    }

    @Override
    public void onWorkerHeartbeat(UUID workerId, Instant timestamp) {
        // Too noisy for metrics — gauges sample on demand
    }

    @Override
    public void onWorkerDied(UUID workerId) {
        // Gauge samples this live — no counter needed
    }

    @Override
    public void onJobAssigned(UUID jobId, UUID workerId, Instant updatedAt) {
        jobsAssignedCounter.increment();
    }

    @Override
    public void onJobRunning(UUID jobId, Instant updatedAt) {
        jobStartTimes.put(jobId, updatedAt);
    }

    @Override
    public void onJobCompleted(UUID jobId, UUID workerId, String result, Instant updatedAt) {
        jobsCompletedCounter.increment();
        recordJobDuration(jobId, updatedAt);
    }

    @Override
    public void onJobFailed(UUID jobId, UUID workerId, String result, Instant updatedAt) {
        jobsFailedCounter.increment();
        recordJobDuration(jobId, updatedAt);
    }

    @Override
    public void onJobCancelled(UUID jobId, UUID workerId, String result, Instant updatedAt) {
        jobsCancelledCounter.increment();
        recordJobDuration(jobId, updatedAt);
    }

    @Override
    public void onJobRequeued(UUID jobId, int retryCount, Instant updatedAt) {
        jobsRequeuedCounter.increment();
        jobRetriesCounter.increment();
        jobStartTimes.remove(jobId);
    }

    // ─── Gauge samplers ──────────────────────────────────────────────────────

    private double totalWorkerCapacity() {
        return workerRegistry.getAll().values().stream()
                .mapToInt(WorkerConnection::getMaxCapacity)
                .sum();
    }

    private double usedWorkerCapacity() {
        return workerRegistry.getAll().values().stream()
                .mapToInt(WorkerConnection::getActiveJobCount)
                .sum();
    }

    private double countJobsInStatusAssigned() {
        return jobRegistry.getAll().values().stream()
                .filter(j -> j.getStatus() == JobStatus.ASSIGNED)
                .count();
    }

    private double countJobsInStatusRunning() {
        return jobRegistry.getAll().values().stream()
                .filter(j -> j.getStatus() == JobStatus.RUNNING)
                .count();
    }

    // ─── Internals ───────────────────────────────────────────────────────────

    /**
     * Increments the submitted counter. Called by {@code JobService.submitJob()}.
     */
    public void incrementJobsSubmitted() {
        jobsSubmittedCounter.increment();
    }

    private void recordJobDuration(UUID jobId, Instant endTime) {
        Instant startTime = jobStartTimes.remove(jobId);
        if (startTime != null) {
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();
            if (durationMs >= 0) {
                jobDurationTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        }
    }
}
