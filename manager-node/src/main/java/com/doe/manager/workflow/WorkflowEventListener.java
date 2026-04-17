package com.doe.manager.workflow;

import com.doe.core.model.Workflow;
import java.util.UUID;

/**
 * Observer interface for workflow state-change events.
 * Implementations receive callbacks whenever the engine mutates workflow state.
 */
public interface WorkflowEventListener {

    /** Fired when a workflow is initially registered (DRAFT status). */
    void onWorkflowRegistered(Workflow workflow);

    /** Fired when a workflow is explicitly deleted from the manager. */
    void onWorkflowDeleted(UUID workflowId);

    /** Fired when a workflow definition is updated (while DRAFT/PAUSED). */
    void onWorkflowUpdated(Workflow workflow);

    /** Fired when a workflow transitions from DRAFT -> RUNNING. */
    void onWorkflowExecuted(Workflow workflow);

    /** Fired when a workflow transitions from RUNNING -> PAUSED. */
    void onWorkflowPaused(Workflow workflow);

    /** Fired when a workflow transitions from PAUSED -> RUNNING. */
    void onWorkflowResumed(Workflow workflow);

    /** Fired when a workflow is reset back to DRAFT (and all jobs to PENDING). */
    void onWorkflowReset(Workflow workflow);

    /** Fired when a workflow reaches a terminal status or its status is otherwise changed (e.g. COMPLETED, FAILED). */
    void onWorkflowStatusChanged(Workflow workflow);
}
