package com.doe.manager.api.dto;

import com.doe.core.model.WorkerStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkerResponse(
        UUID id,
        String hostname,
        String ipAddress,
        WorkerStatus status,
        Instant lastHeartbeat
) {
}
