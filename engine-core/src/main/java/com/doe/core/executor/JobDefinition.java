package com.doe.core.executor;

import java.util.UUID;

/**
 * Record containing task metadata: the unique job ID, the task type, 
 * and the task-specific JSON payload (excluding the type).
 */
public record JobDefinition(
    UUID jobId, 
    UUID workflowId, 
    String label, 
    String type, 
    String payload, 
    long timeoutMs,
    int retryCount,
    String jobToken
) {}
