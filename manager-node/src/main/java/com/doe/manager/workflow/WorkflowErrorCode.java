package com.doe.manager.workflow;

/**
 * Typed error codes for workflow lifecycle failures.
 * Used by {@link WorkflowException} so the REST layer can map
 * each code to the correct HTTP status and error body.
 */
public enum WorkflowErrorCode {

    // 404
    WORKFLOW_NOT_FOUND,

    // 400
    DAG_HAS_CYCLE,
    MISSING_DEPENDENCY,
    INVALID_WORKFLOW_STATE,

    // 409
    WORKFLOW_NOT_EDITABLE,
    WORKFLOW_ALREADY_RUNNING,
    WORKFLOW_NOT_PAUSED,
    WORKFLOW_NOT_DRAFT,
    WORKFLOW_RUNNING,
}
