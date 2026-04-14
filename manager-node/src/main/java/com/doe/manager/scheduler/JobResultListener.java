package com.doe.manager.scheduler;

import com.doe.core.event.EngineEventListener;
import com.doe.core.model.JobStatus;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
import com.doe.core.model.WorkflowStatus;
import com.doe.manager.workflow.WorkflowManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for job completion/failure events and triggers DAG re-evaluation.
 *
 * <p>This component wraps the existing {@link EngineEventListener} chain.
 * When a job transitions to COMPLETED or FAILED, it:
 * <ol>
 *   <li>Looks up which workflow owns the job (via a reverse-index)</li>
 *   <li>Notifies the {@link DagScheduler} to re-evaluate that workflow</li>
 *   <li>Updates the workflow-level status if a terminal state is reached</li>
 * </ol>
 *
 * <p>The reverse-index ({@code jobId → workflowId}) is built lazily: when the
 * listener first encounters a job ID it doesn't know, it scans all RUNNING
 * workflows to find the owner. This avoids scanning all workflows on every
 * event while keeping the index accurate.
 *
 * <p>Thread safety: the reverse-index uses {@link ConcurrentHashMap}. The
 * DagScheduler re-evaluation is delegated to the DagScheduler's own thread
 * safety guarantees.
 */
@Component
public class JobResultListener implements EngineEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(JobResultListener.class);

    private final WorkflowManager workflowManager;
    private final DagScheduler dagScheduler;

    /** Reverse index: jobId → workflowId. Built lazily on first encounter. */
    private final Map<UUID, UUID> jobToWorkflow = new ConcurrentHashMap<>();

    public JobResultListener(WorkflowManager workflowManager, DagScheduler dagScheduler) {
        this.workflowManager = workflowManager;
        this.dagScheduler = dagScheduler;
    }

    // ──── EngineEventListener implementation ─────────────────────────────────

    @Override
    public void onJobCompleted(UUID jobId, UUID workerId, String result, Instant updatedAt) {
        handleJobTerminalEvent(jobId, JobStatus.COMPLETED);
    }

    @Override
    public void onJobFailed(UUID jobId, UUID workerId, String result, Instant updatedAt) {
        handleJobTerminalEvent(jobId, JobStatus.FAILED);
    }

    @Override
    public void onJobAssigned(UUID jobId, UUID workerId, Instant updatedAt) {
        // No-op — DagScheduler handles scheduling, assignment is the JobScheduler's concern
    }

    @Override
    public void onJobRunning(UUID jobId, Instant updatedAt) {
        // No-op — job is running, no scheduling action needed yet
    }

    @Override
    public void onJobCancelled(UUID jobId, UUID workerId, String result, Instant updatedAt) {
        handleJobTerminalEvent(jobId, JobStatus.CANCELLED);
    }

    @Override
    public void onJobRequeued(UUID jobId, int retryCount, Instant updatedAt) {
        // No-op. CrashRecoveryHandler and JobTimeoutMonitor already re-enqueue the job
        // directly via jobQueue.requeue(). The DagScheduler's alreadySubmitted tracker
        // still contains this job ID, which correctly prevents it from being
        // double-enqueued — the JobScheduler will pick it up from the queue naturally.
    }

    @Override
    public void onWorkerRegistered(UUID workerId, String hostname, String ipAddress,
                                    int maxCapacity, Instant registeredAt) {
        // Not relevant for DAG scheduling
    }

    @Override
    public void onWorkerHeartbeat(UUID workerId, Instant timestamp) {
        // Not relevant for DAG scheduling
    }

    @Override
    public void onWorkerDied(UUID workerId) {
        // Not relevant for DAG scheduling — CrashRecoveryHandler handles this
    }

    // ──── Internal logic ─────────────────────────────────────────────────────

    /**
     * Handles a job reaching a terminal or re-runnable state.
     */
    private void handleJobTerminalEvent(UUID jobId, JobStatus newStatus) {
        UUID workflowId = resolveWorkflowForJob(jobId);
        if (workflowId == null) {
            LOG.debug("Job {} is not part of any managed workflow — ignoring", jobId);
            return;
        }

        LOG.info("JobResultListener: job {} → {} in workflow {} — triggering DAG re-evaluation",
                jobId, newStatus, workflowId);

        dagScheduler.onWorkflowJobChanged(workflowId);
    }

    /**
     * Resolves which workflow owns a given job. Uses a cached reverse-index,
     * building it lazily if needed.
     */
    private UUID resolveWorkflowForJob(UUID jobId) {
        UUID cached = jobToWorkflow.get(jobId);
        if (cached != null) {
            return cached;
        }

        // Build the index by scanning all workflows
        buildReverseIndex();
        return jobToWorkflow.get(jobId);
    }

    /**
     * Builds (or refreshes) the jobId → workflowId reverse index by scanning
     * all workflows managed by the WorkflowManager.
     */
    private synchronized void buildReverseIndex() {
        List<Workflow> allWorkflows = workflowManager.listWorkflows();
        for (Workflow wf : allWorkflows) {
            if (wf.getStatus() == WorkflowStatus.RUNNING) {
                for (WorkflowJob wj : wf.getJobs()) {
                    jobToWorkflow.put(wj.getJob().getId(), wf.getId());
                }
            }
        }

        // Clean up entries for workflows that are no longer RUNNING
        // (prevent stale mappings from consuming memory)
        jobToWorkflow.entrySet().removeIf(entry -> {
            Workflow wf = workflowManager.getWorkflow(entry.getValue());
            return wf == null || wf.getStatus() != WorkflowStatus.RUNNING;
        });
    }

    /**
     * Called when a workflow is completed, failed, or deleted.
     * Clears the reverse-index entries for that workflow.
     *
     * @param workflowId the workflow to forget
     */
    public void forgetWorkflow(UUID workflowId) {
        jobToWorkflow.entrySet().removeIf(e -> workflowId.equals(e.getValue()));
    }

    /**
     * Returns the reverse-index for inspection (e.g., in tests).
     */
    public Map<UUID, UUID> getJobToWorkflowIndex() {
        return jobToWorkflow;
    }
}
