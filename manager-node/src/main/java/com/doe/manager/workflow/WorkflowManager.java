package com.doe.manager.workflow;

import com.doe.core.model.Workflow;
import com.doe.core.model.WorkflowJob;
import com.doe.core.model.WorkflowStatus;
import com.doe.core.util.DagValidator;
import com.doe.core.util.DagValidationError;
import com.doe.core.model.Job;
import com.doe.core.model.JobStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.apache.juli.logging.Log;
import org.springframework.stereotype.Service;

/**
 * Thread-safe service that manages the lifecycle of in-memory workflows.
 *
 * <p>Operations provided:
 * <ul>
 *   <li>{@link #registerWorkflow(Workflow)} — validates DAG, stores in memory, sets status to DRAFT</li>
 *   <li>{@link #deleteWorkflow(UUID)} — removes from memory (only if not RUNNING)</li>
 *   <li>{@link #updateWorkflow(UUID, Workflow)} — replaces workflow definition (only when DRAFT or PAUSED)</li>
 *   <li>{@link #executeWorkflow(UUID)} — transitions DRAFT → RUNNING</li>
 *   <li>{@link #pauseWorkflow(UUID)} — transitions RUNNING → PAUSED</li>
 *   <li>{@link #resumeWorkflow(UUID)} — transitions PAUSED → RUNNING</li>
 *   <li>{@link #resetWorkflow(UUID)} — transitions any non-RUNNING state back to DRAFT, resets all job statuses to PENDING</li>
 *   <li>{@link #getWorkflow(UUID)} — returns workflow snapshot</li>
 *   <li>{@link #listWorkflows()} — returns all workflows (optionally filtered by status)</li>
 * </ul>
 *
 * <p>Thread safety is achieved via a {@link ReentrantReadWriteLock} that
 * serialises state mutations while allowing concurrent reads.
 * The backing store is a {@link ConcurrentHashMap} for O(1) lookups.
 */
@Service
public class WorkflowManager {

    private final Log LOG = org.apache.juli.logging.LogFactory.getLog(WorkflowManager.class);

    private final Map<UUID, Workflow> store = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<WorkflowEventListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addListener(WorkflowEventListener listener) {
        listeners.add(listener);
    }

    // ──── Registration ──────────────────────────────────────────────────────

    /**
     * Validates the workflow DAG and stores it in memory with status DRAFT.
     *
     * @param workflow the workflow to register; must be in DRAFT status
     * @return the registered workflow (with enforced DRAFT status)
     * @throws WorkflowException if DAG validation fails or workflow is not DRAFT
     */
    public Workflow registerWorkflow(Workflow workflow) {
        Objects.requireNonNull(workflow, "workflow must not be null");

        if (workflow.getStatus() != WorkflowStatus.DRAFT) {
            throw new WorkflowException(
                    WorkflowErrorCode.INVALID_WORKFLOW_STATE,
                    "Workflow can only be registered in DRAFT status, but was: " + workflow.getStatus());
        }

        // Validate DAG structure
        List<DagValidationError> errors = DagValidator.validate(workflow);
        if (!errors.isEmpty()) {
            String errorDetails = errors.stream()
                    .map(e -> "%s: %s".formatted(e.type(), e.message()))
                    .collect(Collectors.joining("; "));
            // Distinguish cycle vs missing-dependency
            boolean hasCycle = errors.stream()
                    .anyMatch(e -> e.type().name().contains("CYCLE"));
            WorkflowErrorCode code = hasCycle ? WorkflowErrorCode.DAG_HAS_CYCLE : WorkflowErrorCode.MISSING_DEPENDENCY;
            throw new WorkflowException(code, "DAG validation failed: " + errorDetails);
        }

        // Enforce DRAFT status on registration
        Workflow draftWorkflow = enforceStatus(workflow, WorkflowStatus.DRAFT);

        store.put(draftWorkflow.getId(), draftWorkflow);
        listeners.forEach(l -> l.onWorkflowRegistered(draftWorkflow));
        return draftWorkflow;
    }

    /**
     * Registers a workflow recovered from persistent storage.
     * Bypasses DAG validation checks that require all jobs to be PENDING,
     * retains the provided status, and does NOT fire the onWorkflowRegistered event.
     *
     * @param workflow the recovered workflow
     * @return the registered workflow
     */
    public Workflow registerRecoveredWorkflow(Workflow workflow) {
        Objects.requireNonNull(workflow, "workflow must not be null");
        store.put(workflow.getId(), workflow);
        return workflow;
    }

    // ──── Deletion ──────────────────────────────────────────────────────────

    /**
     * Removes a workflow from memory.
     *
     * @param workflowId the ID of the workflow to delete
     * @throws WorkflowException if workflow is RUNNING (cannot delete active workflows)
     * @throws WorkflowException if workflow not found
     */
    public void deleteWorkflow(UUID workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");

        lock.writeLock().lock();
        try {
            Workflow workflow = getWorkflowLocked(workflowId);

            // Cannot delete RUNNING workflows
            if (workflow.getStatus() == WorkflowStatus.RUNNING) {
                throw new WorkflowException(
                        WorkflowErrorCode.WORKFLOW_RUNNING,
                        "Cannot delete a workflow while it is RUNNING. Pause it first.");
            }

            store.remove(workflowId);
            listeners.forEach(l -> l.onWorkflowDeleted(workflowId));
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ──── Update ────────────────────────────────────────────────────────────

    /**
     * Replaces an existing workflow definition. Only allowed when the workflow
     * is in DRAFT or PAUSED status. The new workflow must pass DAG validation
     * and job status consistency checks.
     *
     * <p>Validation rules:
     * <ul>
     *   <li>DAG must be a valid acyclic graph (no self-deps, missing deps, or cycles)</li>
     *   <li>In DRAFT: all jobs must be PENDING</li>
     *   <li>In PAUSED: a job cannot be further along than any of its dependencies
     *       (e.g., a job cannot be COMPLETED if a dependency is still PENDING)</li>
     * </ul>
     *
     * @param workflowId  the ID of the workflow to update
     * @param newWorkflow the replacement workflow definition (must have the same ID)
     * @return the updated workflow
     * @throws WorkflowException if workflow is RUNNING/COMPLETED/FAILED (editing blocked)
     * @throws WorkflowException if DAG validation fails
     * @throws WorkflowException if job statuses violate DAG dependency order
     * @throws WorkflowException if workflow not found
     */
    public Workflow updateWorkflow(UUID workflowId, Workflow newWorkflow) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(newWorkflow, "newWorkflow must not be null");

        if (!workflowId.equals(newWorkflow.getId())) {
            throw new WorkflowException(
                    "Workflow ID mismatch: cannot update workflow %s with definition ID %s"
                            .formatted(workflowId, newWorkflow.getId()));
        }

        lock.writeLock().lock();
        try {
            Workflow existing = getWorkflowLocked(workflowId);

            // Editing is only allowed in DRAFT or PAUSED state
            if (existing.getStatus() == WorkflowStatus.RUNNING) {
                throw new WorkflowException(
                        WorkflowErrorCode.WORKFLOW_NOT_EDITABLE,
                        "Cannot edit a workflow while it is RUNNING. Pause it first.");
            }
            if (existing.getStatus() == WorkflowStatus.COMPLETED) {
                throw new WorkflowException(
                        WorkflowErrorCode.WORKFLOW_NOT_EDITABLE,
                        "Cannot edit a workflow that is COMPLETED. Reset it to DRAFT first.");
            }
            if (existing.getStatus() == WorkflowStatus.FAILED) {
                throw new WorkflowException(
                        WorkflowErrorCode.WORKFLOW_NOT_EDITABLE,
                        "Cannot edit a workflow that is FAILED. Reset it to DRAFT first.");
            }

            // Validate the new DAG structure
            List<DagValidationError> errors = DagValidator.validate(newWorkflow);
            if (!errors.isEmpty()) {
                String errorDetails = errors.stream()
                        .map(e -> "%s: %s".formatted(e.type(), e.message()))
                        .collect(Collectors.joining("; "));
                boolean hasCycle = errors.stream()
                        .anyMatch(e -> e.type().name().contains("CYCLE"));
                WorkflowErrorCode code = hasCycle ? WorkflowErrorCode.DAG_HAS_CYCLE : WorkflowErrorCode.MISSING_DEPENDENCY;
                throw new WorkflowException(code, "DAG validation failed: " + errorDetails);
            }

            // Validate that job statuses are consistent with DAG dependency order
            validateJobStatusConsistency(newWorkflow, existing.getStatus());

            // Preserve the existing status
            Workflow updatedWorkflow = enforceStatus(newWorkflow, existing.getStatus());
            store.put(workflowId, updatedWorkflow);
            listeners.forEach(l -> l.onWorkflowUpdated(updatedWorkflow));
            return updatedWorkflow;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ──── Execute ───────────────────────────────────────────────────────────

    /**
     * Transitions a workflow from DRAFT → RUNNING.
     *
     * @param workflowId the ID of the workflow to execute
     * @return the updated workflow with RUNNING status
     * @throws WorkflowException if workflow is not in DRAFT status
     * @throws WorkflowException if workflow not found
     */
    public Workflow executeWorkflow(UUID workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");

        LOG.info(String.format("executeWorkflow called for workflowId=%s", workflowId));

        lock.writeLock().lock();
        try {
            Workflow workflow = getWorkflowLocked(workflowId);
            LOG.info(String.format("executeWorkflow: Current status of workflow %s is %s", workflowId, workflow.getStatus()));

            if (workflow.getStatus() == WorkflowStatus.RUNNING) {
                throw new WorkflowException(
                        WorkflowErrorCode.WORKFLOW_ALREADY_RUNNING,
                        "Workflow %s is already RUNNING".formatted(workflowId));
            }
            if (workflow.getStatus() != WorkflowStatus.DRAFT) {
                throw new WorkflowException(
                        WorkflowErrorCode.WORKFLOW_NOT_DRAFT,
                        "Can only execute workflows in DRAFT status, but workflow %s is %s"
                                .formatted(workflowId, workflow.getStatus()));
            }

            LOG.info(String.format("executeWorkflow: Changing workflow %s status to RUNNING", workflowId));
            Workflow runningWorkflow = workflow.withStatus(WorkflowStatus.RUNNING);
            store.put(workflowId, runningWorkflow);
            listeners.forEach(l -> l.onWorkflowExecuted(runningWorkflow));
            LOG.info(String.format("executeWorkflow: Workflow %s successfully executed", workflowId));
            return runningWorkflow;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ──── Pause ─────────────────────────────────────────────────────────────

    /**
     * Transitions a workflow from RUNNING → PAUSED.
     *
     * @param workflowId the ID of the workflow to pause
     * @return the updated workflow with PAUSED status
     * @throws WorkflowException if workflow is not in RUNNING status
     * @throws WorkflowException if workflow not found
     */
    public Workflow pauseWorkflow(UUID workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");

        LOG.info(String.format("pauseWorkflow called for workflowId=%s", workflowId));

        lock.writeLock().lock();
        try {
            Workflow workflow = getWorkflowLocked(workflowId);
            LOG.info(String.format("pauseWorkflow: Current status of workflow %s is %s", workflowId, workflow.getStatus()));

            if (workflow.getStatus() != WorkflowStatus.RUNNING) {
                throw new WorkflowException(
                        WorkflowErrorCode.INVALID_WORKFLOW_STATE,
                        "Can only pause workflows in RUNNING status, but workflow %s is %s"
                                .formatted(workflowId, workflow.getStatus()));
            }

            LOG.info(String.format("pauseWorkflow: Changing workflow %s status to PAUSED", workflowId));
            Workflow pausedWorkflow = workflow.withStatus(WorkflowStatus.PAUSED);
            store.put(workflowId, pausedWorkflow);
            listeners.forEach(l -> l.onWorkflowPaused(pausedWorkflow));
            LOG.info(String.format("pauseWorkflow: Workflow %s successfully paused", workflowId));
            return pausedWorkflow;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ──── Resume ────────────────────────────────────────────────────────────

    /**
     * Transitions a workflow from PAUSED → RUNNING.
     *
     * @param workflowId the ID of the workflow to resume
     * @return the updated workflow with RUNNING status
     * @throws WorkflowException if workflow is not in PAUSED status
     * @throws WorkflowException if workflow not found
     */
    public Workflow resumeWorkflow(UUID workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");

        lock.writeLock().lock();
        try {
            Workflow workflow = getWorkflowLocked(workflowId);

            if (workflow.getStatus() != WorkflowStatus.PAUSED) {
                throw new WorkflowException(
                        WorkflowErrorCode.WORKFLOW_NOT_PAUSED,
                        "Can only resume workflows in PAUSED status, but workflow %s is %s"
                                .formatted(workflowId, workflow.getStatus()));
            }

            Workflow runningWorkflow = workflow.withStatus(WorkflowStatus.RUNNING);
            store.put(workflowId, runningWorkflow);
            listeners.forEach(l -> l.onWorkflowResumed(runningWorkflow));
            return runningWorkflow;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ──── Reset ─────────────────────────────────────────────────────────────

    /**
     * Resets a workflow back to DRAFT status. All job statuses are reset to PENDING.
     *
     * <p>Valid from: PAUSED, COMPLETED, FAILED
     * <p>Invalid from: RUNNING (must pause first), DRAFT (already in DRAFT)
     *
     * @param workflowId the ID of the workflow to reset
     * @return the reset workflow with DRAFT status and all jobs in PENDING status
     * @throws WorkflowException if workflow is RUNNING
     * @throws WorkflowException if workflow not found
     */
    public Workflow resetWorkflow(UUID workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");

        LOG.info(String.format("resetWorkflow called for workflowId=%s", workflowId));

        lock.writeLock().lock();
        try {
            Workflow workflow = getWorkflowLocked(workflowId);
            LOG.info(String.format("resetWorkflow: Current status of workflow %s is %s", workflowId, workflow.getStatus()));

            if (workflow.getStatus() == WorkflowStatus.RUNNING) {
                throw new WorkflowException(
                        WorkflowErrorCode.WORKFLOW_RUNNING,
                        "Cannot reset a workflow while it is RUNNING. Pause it first.");
            }

            if (workflow.getStatus() == WorkflowStatus.DRAFT) {
                LOG.info(String.format("resetWorkflow: Workflow %s is already DRAFT. Just resetting job statuses.", workflowId));
                // Already in DRAFT — nothing to reset, but still reset job statuses
                Workflow resetWorkflow = resetJobStatuses(workflow);
                store.put(workflowId, resetWorkflow);
                listeners.forEach(l -> l.onWorkflowReset(resetWorkflow));
                LOG.info(String.format("resetWorkflow: Workflow %s reset successfully (was already DRAFT)", workflowId));
                return resetWorkflow;
            }

            LOG.info(String.format("resetWorkflow: Restoring workflow %s to DRAFT and resetting job statuses", workflowId));
            // Transition to DRAFT and reset all job statuses to PENDING
            final Workflow finalResetWorkflow = resetJobStatuses(workflow.withStatus(WorkflowStatus.DRAFT));
            store.put(workflowId, finalResetWorkflow);
            listeners.forEach(l -> l.onWorkflowReset(finalResetWorkflow));
            LOG.info(String.format("resetWorkflow: Workflow %s successfully restored to DRAFT", workflowId));
            return finalResetWorkflow;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ──── Read Operations ───────────────────────────────────────────────────

    /**
     * Returns a snapshot of the workflow, or null if not found.
     *
     * @param workflowId the ID of the workflow
     * @return the workflow, or null if not found
     */
    public Workflow getWorkflow(UUID workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");

        lock.readLock().lock();
        try {
            return store.get(workflowId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all workflows, optionally filtered by status.
     *
     * @param status if non-null, only return workflows matching this status
     * @return an unmodifiable list of workflow snapshots
     */
    public List<Workflow> listWorkflows(WorkflowStatus status) {
        lock.readLock().lock();
        try {
            if (status == null) {
                return List.copyOf(store.values());
            }
            return store.values().stream()
                    .filter(w -> w.getStatus() == status)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns all workflows (no filter).
     *
     * @return an unmodifiable list of all workflow snapshots
     */
    public List<Workflow> listWorkflows() {
        return listWorkflows(null);
    }

    /**
     * Returns the number of workflows currently in memory.
     */
    public int workflowCount() {
        return store.size();
    }

    /**
     * Transitions a workflow from RUNNING → COMPLETED.
     *
     * @param workflowId the ID of the workflow to complete
     * @return the updated workflow with COMPLETED status
     * @throws WorkflowException if workflow is not in RUNNING status
     * @throws WorkflowException if workflow not found
     */
    public Workflow completeWorkflow(UUID workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");

        lock.writeLock().lock();
        try {
            Workflow workflow = getWorkflowLocked(workflowId);

            if (workflow.getStatus() != WorkflowStatus.RUNNING) {
                throw new WorkflowException(
                        "Can only complete workflows in RUNNING status, but workflow %s is %s"
                                .formatted(workflowId, workflow.getStatus()));
            }

            Workflow completedWorkflow = workflow.withStatus(WorkflowStatus.COMPLETED);
            store.put(workflowId, completedWorkflow);
            listeners.forEach(l -> l.onWorkflowStatusChanged(completedWorkflow));
            return completedWorkflow;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Transitions a workflow from RUNNING → FAILED.
     *
     * @param workflowId the ID of the workflow to fail
     * @return the updated workflow with FAILED status
     * @throws WorkflowException if workflow is not in RUNNING status
     * @throws WorkflowException if workflow not found
     */
    public Workflow failWorkflow(UUID workflowId) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");

        lock.writeLock().lock();
        try {
            Workflow workflow = getWorkflowLocked(workflowId);

            if (workflow.getStatus() != WorkflowStatus.RUNNING) {
                throw new WorkflowException(
                        "Can only fail workflows in RUNNING status, but workflow %s is %s"
                                .formatted(workflowId, workflow.getStatus()));
            }

            Workflow failedWorkflow = workflow.withStatus(WorkflowStatus.FAILED);
            store.put(workflowId, failedWorkflow);
            listeners.forEach(l -> l.onWorkflowStatusChanged(failedWorkflow));
            return failedWorkflow;
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ──── Internal Helpers ──────────────────────────────────────────────────

    /**
     * Validates that job statuses are consistent with DAG dependency order.
     *
     * <p>Rules:
     * <ul>
     *   <li>In DRAFT: all jobs must be PENDING</li>
     *   <li>In PAUSED: a job cannot be further along than any of its dependencies
     *       (e.g., a job cannot be COMPLETED if a dependency is still PENDING)</li>
     * </ul>
     *
     * @param workflow the workflow to validate
     * @param status   the current workflow status (DRAFT or PAUSED)
     * @throws WorkflowException if job statuses violate DAG ordering
     */
    private void validateJobStatusConsistency(Workflow workflow, WorkflowStatus status) {
        if (status == WorkflowStatus.DRAFT) {
            // All jobs must be PENDING in DRAFT state
            for (WorkflowJob wj : workflow.getJobs()) {
                com.doe.core.model.Job job = wj.getJob();
                if (job.getStatus() != com.doe.core.model.JobStatus.PENDING) {
                    throw new WorkflowException(
                            "Workflow is in DRAFT status, but job %s has status %s (expected PENDING)"
                                    .formatted(job.getId(), job.getStatus()));
                }
            }
            return;
        }

        // PAUSED: a job cannot be further along than any of its dependencies
        // to map job IDs to their statuses for quick lookup
        Map<UUID, WorkflowJob> jobMap = new java.util.HashMap<>();
        for (WorkflowJob wj : workflow.getJobs()) {
            jobMap.put(wj.getJob().getId(), wj);
        }

        for (WorkflowJob wj : workflow.getJobs()) {
            int jobProgress = progressOrdinal(wj.getJob().getStatus());
            for (UUID depId : wj.getDependencies()) {
                WorkflowJob depWj = jobMap.get(depId);
                if (depWj == null) {
                    // Missing dependency — already caught by DagValidator, skip here
                    LOG.warn("Job %s depends on missing job %s — skipping status consistency check for this dependency"
                            .formatted(wj.getJob().getId(), depId));
                    continue;
                }// All jobs must be PENDING in DRAFT state
                int depProgress = progressOrdinal(depWj.getJob().getStatus());
                if (depProgress < jobProgress) {
                    throw new WorkflowException(
                            "Invalid job status: job %s is %s but its dependency %s is %s — "
                                    + "a job cannot be further along than its dependencies"
                                    .formatted(wj.getJob().getId(), wj.getJob().getStatus(),
                                            depId, depWj.getJob().getStatus()));
                }
            }
        }
    }

    /**
     * Returns a numeric progress level for a job status.
     * Higher values mean the job has progressed further.
     */
    private static int progressOrdinal(com.doe.core.model.JobStatus status) {
        return switch (status) {
            case PENDING    -> 0;
            case ASSIGNED   -> 1;
            case RUNNING    -> 2;
            case COMPLETED  -> 3;
            case FAILED     -> 3; // terminal, same level as completed
            case CANCELLED  -> 3; // terminal, same level as completed
        };
    }

    /**
     * Retrieves a workflow from the store, throwing if not found.
     * Must be called while holding the write lock.
     */
    private Workflow getWorkflowLocked(UUID workflowId) {
        Workflow workflow = store.get(workflowId);
        if (workflow == null) {
            throw new WorkflowException(
                    WorkflowErrorCode.WORKFLOW_NOT_FOUND,
                    "Workflow not found: " + workflowId);
        }
        return workflow;
    }

    /**
     * Returns a new workflow instance with the given status, preserving all other fields.
     */
    private Workflow enforceStatus(Workflow workflow, WorkflowStatus desiredStatus) {
        if (workflow.getStatus() == desiredStatus) {
            return workflow;
        }
        return workflow.withStatus(desiredStatus);
    }

    /**
     * Returns a new workflow instance with all job statuses reset to PENDING.
     */
    private Workflow resetJobStatuses(Workflow workflow) {
        LOG.info(String.format("resetJobStatuses: Starting job reset for workflow %s", workflow.getId()));
        List<WorkflowJob> resetJobs = workflow.getJobs().stream()
                .map(wj -> {
                    Job job = wj.getJob();
                    LOG.info(String.format("resetJobStatuses: Resetting job %s (current status=%s) to PENDING", job.getId(), job.getStatus()));
                    Job resetJob = Job.newJob(job.getPayload())
                            .id(job.getId())
                            .status(JobStatus.PENDING)
                            .timeoutMs(job.getTimeoutMs())
                            .createdAt(job.getCreatedAt())
                            .updatedAt(Instant.now())
                            .build();
                    return WorkflowJob.fromJob(resetJob)
                            .dagIndex(wj.getDagIndex())
                            .dependencies(wj.getDependencies())
                            .build();
                })
                .collect(Collectors.toList());

        LOG.info(String.format("resetJobStatuses: Creating new workflow instance %s", workflow.getId()));
        return Workflow.newWorkflow(workflow.getName())
                .id(workflow.getId())
                .status(workflow.getStatus())
                .addJobs(resetJobs)
                .createdAt(workflow.getCreatedAt())
                .build();
    }
}
