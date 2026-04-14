package com.doe.manager.workflow;

/**
 * Exception thrown when a workflow lifecycle operation fails
 * (e.g., invalid state transition, workflow not found, editing blocked).
 */
public class WorkflowException extends RuntimeException {

    public WorkflowException(String message) {
        super(message);
    }

    public WorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
