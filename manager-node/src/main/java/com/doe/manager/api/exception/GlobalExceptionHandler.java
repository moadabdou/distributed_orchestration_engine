package com.doe.manager.api.exception;

import com.doe.manager.workflow.WorkflowErrorCode;
import com.doe.manager.workflow.WorkflowException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(com.doe.manager.scheduler.JobQueueFullException.class)
    public ResponseEntity<Object> handleJobQueueFullException(com.doe.manager.scheduler.JobQueueFullException ex) {
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, "QUEUE_FULL", ex.getMessage());
    }

    @ExceptionHandler(WorkflowException.class)
    public ResponseEntity<Object> handleWorkflowException(WorkflowException ex) {
        HttpStatus status = resolveHttpStatus(ex.getErrorCode());
        return buildErrorResponse(status, ex.getErrorCode().name(), ex.getMessage());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static HttpStatus resolveHttpStatus(WorkflowErrorCode code) {
        return switch (code) {
            case WORKFLOW_NOT_FOUND                     -> HttpStatus.NOT_FOUND;
            case DAG_HAS_CYCLE, MISSING_DEPENDENCY,
                 INVALID_WORKFLOW_STATE, WORKFLOW_NOT_DRAFT -> HttpStatus.BAD_REQUEST;
            case WORKFLOW_NOT_EDITABLE,
                 WORKFLOW_ALREADY_RUNNING,
                 WORKFLOW_NOT_PAUSED,
                 WORKFLOW_RUNNING                       -> HttpStatus.CONFLICT;
        };
    }

    private ResponseEntity<Object> buildErrorResponse(HttpStatus status, String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("code", errorCode);
        body.put("message", message);
        return new ResponseEntity<>(body, status);
    }
}
