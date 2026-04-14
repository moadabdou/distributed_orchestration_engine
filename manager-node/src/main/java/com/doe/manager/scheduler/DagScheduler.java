package com.doe.manager.scheduler;

import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;
import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
import com.doe.core.model.WorkflowStatus;
import com.doe.manager.workflow.WorkflowManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DAG-aware scheduler that releases jobs to the {@link JobQueue} only when
 * their dependencies are satisfied.
 *
 * <p>It wraps the existing FIFO {@link JobScheduler} — the DagScheduler's sole
 * responsibility is to inspect RUNNING workflows, find jobs whose dependencies
 * are ALL COMPLETED, and enqueue those jobs. The existing JobScheduler then
 * handles worker assignment as usual.
 *
 * <pre>
 * For each RUNNING workflow:
 *   1. Find all jobs in PENDING status whose dependencies are ALL COMPLETED
 *   2. Submit those jobs to the JobQueue (FIFO)
 *   3. When a job completes/fails, update the workflow's job state and re-evaluate
 *   4. If ALL jobs COMPLETED → workflow status = COMPLETED
 *   5. If ANY job FAILED → workflow status = FAILED (fail-fast or continue)
 * </pre>
 *
 * <p>Supports scheduling patterns:
 * <ul>
 *   <li>Linear DAG (A→B→C)</li>
 *   <li>Fan-out (A→B, C)</li>
 *   <li>Fan-in (B, C→D)</li>
 *   <li>Diamond (A→B, C→D)</li>
 *   <li>Multi-workflow interleaving</li>
 * </ul>
 *
 * <p>Thread safety: the scheduler runs on a dedicated timer thread. Workflow
 * mutations are delegated to {@link WorkflowManager} which uses its own
 * {@code ReentrantReadWriteLock}. The {@code submittedJobs} tracker uses
 * {@link ConcurrentHashMap} and {@code Collections.newSetFromMap()} for
 * thread-safe per-workflow tracking.
 */
@Component
public class DagScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DagScheduler.class);

    private final WorkflowManager workflowManager;
    private final JobQueue jobQueue;
    private final long schedulerIntervalMs;
    private final boolean failFast;
    private final int maxConcurrentJobsPerWorkflow;

    /** Tracks which jobs have already been submitted to the queue per workflow, to avoid double-submission. */
    private final Map<UUID, Set<UUID>> submittedJobs = new ConcurrentHashMap<>();

    private final ScheduledExecutorService schedulerExecutor;
    private volatile boolean running;

    public DagScheduler(
            WorkflowManager workflowManager,
            JobQueue jobQueue,
            @Value("${doe.workflow.scheduler-interval-ms:1000}") long schedulerIntervalMs,
            @Value("${doe.workflow.fail-fast:true}") boolean failFast,
            @Value("${doe.workflow.max-concurrent-jobs-per-workflow:10}") int maxConcurrentJobsPerWorkflow) {

        this.workflowManager = workflowManager;
        this.jobQueue = jobQueue;
        this.schedulerIntervalMs = schedulerIntervalMs;
        this.failFast = failFast;
        this.maxConcurrentJobsPerWorkflow = maxConcurrentJobsPerWorkflow;
        this.schedulerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dag-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the periodic scheduler. No-op if already started.
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        schedulerExecutor.scheduleAtFixedRate(
                this::schedulingTick,
                0,
                schedulerIntervalMs,
                TimeUnit.MILLISECONDS);
        LOG.info("DagScheduler started (interval={}ms, failFast={}, maxConcurrentJobsPerWorkflow={})",
                schedulerIntervalMs, failFast, maxConcurrentJobsPerWorkflow);
    }

    /**
     * Stops the periodic scheduler.
     */
    public synchronized void stop() {
        running = false;
        schedulerExecutor.shutdownNow();
        LOG.info("DagScheduler stopped");
    }

    /**
     * Called by {@link JobResultListener} when a job completes or fails.
     * Immediately triggers a re-evaluation of the owning workflow.
     *
     * @param workflowId the workflow that contains the completed/failed job
     */
    public void onWorkflowJobChanged(UUID workflowId) {
        if (!running) {
            return;
        }
        try {
            evaluateWorkflow(workflowId);
        } catch (Exception e) {
            LOG.error("Error evaluating workflow {} after job change", workflowId, e);
        }
    }

    /**
     * Single scheduling tick: scans all RUNNING workflows and releases ready jobs.
     */
    private void schedulingTick() {
        try {
            List<Workflow> runningWorkflows = workflowManager.listWorkflows(WorkflowStatus.RUNNING);
            for (Workflow workflow : runningWorkflows) {
                evaluateWorkflow(workflow.getId());
            }
        } catch (Exception e) {
            LOG.error("Error in DagScheduler tick", e);
        }
    }

    /**
     * Evaluates a single workflow: finds ready jobs, enqueues them, and checks
     * if the workflow has reached a terminal state.
     */
    private void evaluateWorkflow(UUID workflowId) {
        Workflow workflow = workflowManager.getWorkflow(workflowId);
        if (workflow == null || workflow.getStatus() != WorkflowStatus.RUNNING) {
            return;
        }

        Set<UUID> alreadySubmitted = submittedJobs.computeIfAbsent(workflowId, k ->
                Collections.newSetFromMap(new ConcurrentHashMap<>()));

        List<WorkflowJob> readyJobs = findReadyJobs(workflow, alreadySubmitted);

        if (readyJobs.isEmpty()) {
            // No ready jobs — check if workflow is done
            checkTerminalState(workflow);
            return;
        }

        // Enqueue ready jobs (respecting max-concurrent limit)
        int runningJobCount = countRunningJobs(workflow, alreadySubmitted);
        int enqueued = 0;
        for (WorkflowJob wj : readyJobs) {
            if (runningJobCount + enqueued >= maxConcurrentJobsPerWorkflow) {
                LOG.debug("Workflow {} reached max concurrent jobs limit ({})", workflowId, maxConcurrentJobsPerWorkflow);
                break;
            }

            Job job = wj.getJob();
            // Double-check the job is still PENDING (may have changed since we read it)
            if (job.getStatus() != JobStatus.PENDING) {
                alreadySubmitted.add(job.getId()); 
                continue;
            }

            jobQueue.enqueue(job);
            alreadySubmitted.add(job.getId());
            enqueued++;
            LOG.info("DagScheduler: enqueued job {} from workflow {} (deps satisfied)",
                    job.getId(), workflowId);
        }
    }

    /**
     * Finds all PENDING jobs whose dependencies are ALL COMPLETED and haven't
     * been submitted yet.
     */
    private List<WorkflowJob> findReadyJobs(Workflow workflow, Set<UUID> alreadySubmitted) {
        List<WorkflowJob> ready = new ArrayList<>();

        for (WorkflowJob wj : workflow.getJobs()) {
            Job job = wj.getJob();

            // Skip if not PENDING or already submitted
            if (job.getStatus() != JobStatus.PENDING || alreadySubmitted.contains(job.getId())) {
                continue;
            }

            // Check if all dependencies are COMPLETED
            if (areDependenciesSatisfied(wj, workflow)) {
                ready.add(wj);
            } else if (failFast) {
                // Check if any dependency FAILED — if so, this job should never run
                if (hasFailedDependency(wj, workflow)) {
                    alreadySubmitted.add(job.getId());
                    failJobWithUnmetDependencies(workflow.getId(), job);
                }
            }
        }

        return ready;
    }

    /**
     * Returns true if all dependencies of the given workflow job are COMPLETED.
     * Jobs with no dependencies return true.
     */
    private boolean areDependenciesSatisfied(WorkflowJob wj, Workflow workflow) {
        for (UUID depId : wj.getDependencies()) {
            WorkflowJob depWj = workflow.getJob(depId);
            if (depWj == null) {
                // Missing dependency — treat as not satisfied
                LOG.warn("Job {} depends on missing job {} — will not be scheduled",
                        wj.getJob().getId(), depId);
                return false;
            }
            if (depWj.getJob().getStatus() != JobStatus.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if any dependency of the given workflow job has FAILED or CANCELLED.
     */
    private boolean hasFailedDependency(WorkflowJob wj, Workflow workflow) {
        for (UUID depId : wj.getDependencies()) {
            WorkflowJob depWj = workflow.getJob(depId);
            if (depWj != null && (depWj.getJob().getStatus() == JobStatus.FAILED
                    || depWj.getJob().getStatus() == JobStatus.CANCELLED)) {
                return true;
            }
            // Recursively check transitive dependencies
            if (depWj != null && hasFailedDependency(depWj, workflow)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marks a job as CANCELLED because its dependencies could not be met (fail-fast mode).
     * PENDING → CANCELLED is a valid transition; the job was never dispatched.
     */
    private void failJobWithUnmetDependencies(UUID workflowId, Job job) {
        job.transition(JobStatus.CANCELLED);
        job.setResult("Skipped: dependency failed (fail-fast mode)");
        LOG.info("DagScheduler: cancelled job {} in workflow {} due to unmet dependencies",
                job.getId(), workflowId);
    }

    /**
     * Counts how many jobs in this workflow are currently "in flight" — meaning
     * they are already in the queue or actively being processed by workers.
     * Used to enforce the max-concurrent-jobs-per-workflow limit.
     *
     * <p>Terminal jobs (COMPLETED / FAILED / CANCELLED) are excluded.
     * Pure PENDING jobs that haven't been enqueued yet are also excluded —
     * those are "ready to schedule", not "in flight".
     */
    private int countRunningJobs(Workflow workflow, Set<UUID> alreadySubmitted) {
        int count = 0;
        for (WorkflowJob wj : workflow.getJobs()) {
            JobStatus s = wj.getJob().getStatus();
            if (s == JobStatus.COMPLETED || s == JobStatus.FAILED || s == JobStatus.CANCELLED) {
                continue;
            }
            if (alreadySubmitted.contains(wj.getJob().getId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Checks whether the workflow has reached a terminal state (all jobs COMPLETED,
     * or at least one FAILED). If so, transitions the workflow accordingly.
     */
    private void checkTerminalState(Workflow workflow) {
        UUID workflowId = workflow.getId();
        boolean allTerminal = true;
        boolean anyNonSuccess = false; // FAILED or CANCELLED

        for (WorkflowJob wj : workflow.getJobs()) {
            JobStatus status = wj.getJob().getStatus();
            switch (status) {
                case COMPLETED:
                    // terminal, success — ok
                    break;
                case FAILED:
                case CANCELLED:
                    // terminal, non-success
                    anyNonSuccess = true;
                    break;
                default:
                    // PENDING, ASSIGNED, RUNNING — not yet terminal
                    allTerminal = false;
                    break;
            }
        }

        if (allTerminal && anyNonSuccess) {
            // All jobs terminal, at least one failed/cancelled
            try {
                workflowManager.failWorkflow(workflowId);
                LOG.info("DagScheduler: workflow {} FAILED (at least one job failed/cancelled)", workflowId);
            } catch (Exception e) {
                LOG.debug("Could not fail workflow {} (may already be terminal): {}", workflowId, e.getMessage());
            }
        } else if (allTerminal && !anyNonSuccess) {
            // All jobs completed successfully
            try {
                workflowManager.completeWorkflow(workflowId);
                LOG.info("DagScheduler: workflow {} COMPLETED (all jobs done)", workflowId);
            } catch (Exception e) {
                LOG.debug("Could not complete workflow {} (may already be terminal): {}", workflowId, e.getMessage());
            }
        }
    }

    /**
     * Clears the entire submitted-jobs tracker for a workflow.
     * Called when a workflow is reset or deleted so that stale tracking doesn't interfere.
     */
    public void forgetWorkflow(UUID workflowId) {
        submittedJobs.remove(workflowId);
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the submitted-jobs tracker for inspection (e.g., in tests).
     */
    public Map<UUID, Set<UUID>> getSubmittedJobs() {
        return submittedJobs;
    }
}
