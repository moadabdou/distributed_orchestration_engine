package com.doe.manager.api.dto;

/**
 * DTO for job definition within a workflow request.
 * Contains only the fields necessary for defining a job in a DAG.
 */
public record JobDefinition(
        String label,
        String type,
        String payload,
        long timeoutMs,
        int retryCount
) {}
