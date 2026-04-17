package com.doe.manager.api.dto;

import com.doe.core.model.WorkflowStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Full workflow detail response — returned by single-workflow endpoints.
 */
public record WorkflowResponse(
        UUID id,
        String name,
        WorkflowStatus status,
        int totalJobs,
        int completedJobs,
        int failedJobs,
        int pendingJobs,
        Instant createdAt,
        Instant updatedAt
) {}
