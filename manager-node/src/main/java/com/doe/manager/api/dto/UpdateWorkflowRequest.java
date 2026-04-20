package com.doe.manager.api.dto;

import com.doe.core.executor.JobDefinition;
import java.util.List;

/**
 * Request body for {@code PUT /api/v1/workflows/{id}}.
 * Same shape as {@link CreateWorkflowRequest} — full replacement semantics.
 */
public record UpdateWorkflowRequest(
        String name,
        List<JobDefinition> jobs,
        List<CreateWorkflowRequest.DependencyEdge> dependencies
) {}
