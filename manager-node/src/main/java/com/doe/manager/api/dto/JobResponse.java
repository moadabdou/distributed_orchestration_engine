package com.doe.manager.api.dto;

import com.doe.core.model.JobStatus;
import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        JobStatus status,
        String payload,
        String result,
        UUID workerId,
        int retryCount,
        Instant createdAt,
        Instant updatedAt
) {
}
