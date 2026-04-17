package com.doe.manager.workflow;

/**
 * Exception thrown when a workflow lifecycle operation fails.
 * Carries a typed {@link WorkflowErrorCode} so the REST layer
 * can map it to the correct HTTP status and error body.
 */
public class WorkflowException extends RuntimeException {

    private final WorkflowErrorCode errorCode;

    public WorkflowException(WorkflowErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public WorkflowException(WorkflowErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** Backward-compat constructor — defaults to INVALID_WORKFLOW_STATE. */
    public WorkflowException(String message) {
        this(WorkflowErrorCode.INVALID_WORKFLOW_STATE, message);
    }

    public WorkflowErrorCode getErrorCode() {
        return errorCode;
    }
}
