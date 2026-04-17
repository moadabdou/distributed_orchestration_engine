package com.doe.manager.api.dto;

import com.doe.core.model.WorkflowStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight workflow summary for the paginated list endpoint.
 */
public record WorkflowSummaryResponse(
        UUID id,
        String name,
        WorkflowStatus status,
        int totalJobs,
        Instant createdAt
) {}
