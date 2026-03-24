package com.doe.manager.scheduler;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.registry.JobRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically scans the {@link JobRegistry} for jobs that have been {@code ASSIGNED} or {@code RUNNING}
 * for longer than their configured {@code timeoutMs} (plus a short buffer). Timeouts trigger recovery 
 * via the {@link CrashRecoveryHandler}.
 */
public class JobTimeoutMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(JobTimeoutMonitor.class);
    // Buffer time to allow late JOB_RESULT messages to arrive before killing a sticking job.
    private static final long BUFFER_MS = 5000;
    
    private final JobRegistry jobRegistry;
    private final CrashRecoveryHandler recoveryHandler;
    private final long checkIntervalMs;
    private ScheduledExecutorService executor;
    private volatile boolean running;

    /**
     * Creates a new timeout monitor checking every 10 seconds.
     */
    public JobTimeoutMonitor(JobRegistry jobRegistry, CrashRecoveryHandler recoveryHandler) {
        this(jobRegistry, recoveryHandler, 10_000);
    }

    public JobTimeoutMonitor(JobRegistry jobRegistry, CrashRecoveryHandler recoveryHandler, long checkIntervalMs) {
        this.jobRegistry = jobRegistry;
        this.recoveryHandler = recoveryHandler;
        this.checkIntervalMs = checkIntervalMs;
    }

    public void start() {
        if (running) return;
        running = true;
        
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "job-timeout-monitor");
            t.setDaemon(true);
            return t;
        });

        executor.scheduleAtFixedRate(this::checkTimeouts, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
        LOG.info("JobTimeoutMonitor started (interval: {} ms)", checkIntervalMs);
    }

    public void stop() {
        running = false;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        LOG.info("JobTimeoutMonitor stopped");
    }

    private void checkTimeouts() {
        try {
            long now = Instant.now().toEpochMilli();
            for (Job job : jobRegistry.values()) {
                JobStatus status = job.getStatus();
                if (status == JobStatus.ASSIGNED || status == JobStatus.RUNNING) {
                    long elapsed = now - job.getUpdatedAt().toEpochMilli();
                    long maxAllowed = job.getTimeoutMs() + BUFFER_MS;
                    if (elapsed > maxAllowed) {
                        LOG.warn("Job {} exceeded timeout! Status: {}, Elapsed: {}ms, Allowed: {}ms", 
                                 job.getId(), status, elapsed, maxAllowed);
                        recoveryHandler.recoverTimedOutJob(job);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Unexpected error in JobTimeoutMonitor", e);
        }
    }
}
