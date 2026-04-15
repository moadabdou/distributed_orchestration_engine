package com.doe.manager.api.dto;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/workflows}.
 * Jobs are referenced by label; the backend resolves labels to UUIDs.
 */
public record CreateWorkflowRequest(
        String name,
        List<JobDefinition> jobs,
        List<DependencyEdge> dependencies
) {

    public record JobDefinition(
            String label,
            String payload,
            Long timeoutMs,
            Integer retryCount
    ) {}

    public record DependencyEdge(
            String fromJobLabel,
            String toJobLabel
    ) {}
}
