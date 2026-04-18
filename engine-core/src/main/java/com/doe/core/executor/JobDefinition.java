package com.doe.core.executor;

import java.util.UUID;

/**
 * Record containing task metadata: the unique job ID, the task type, 
 * and the task-specific JSON payload (excluding the type).
 */
public record JobDefinition(UUID jobId, String type, String payload) {}
