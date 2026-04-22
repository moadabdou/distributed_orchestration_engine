package com.doe.manager.api.dto;

import com.doe.core.model.JobStatus;
import com.doe.core.model.WorkflowStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full DAG representation response — nodes (jobs) + directed edges (dependencies).
 */
public record DagGraphResponse(
        UUID workflowId,
        String workflowName,
        WorkflowStatus workflowStatus,
        List<DagNodeResponse> nodes,
        List<DagEdgeResponse> edges,
        List<DagEdgeResponse> dataEdges
) {

    public record DagNodeResponse(
            UUID jobId,
            String label,
            int dagIndex,
            JobStatus status,
            String payload,
            String result,
            UUID workerId,
            long timeoutMs,
            String jobLabel,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record DagEdgeResponse(
            UUID sourceJobId,
            UUID targetJobId
    ) {}
}
