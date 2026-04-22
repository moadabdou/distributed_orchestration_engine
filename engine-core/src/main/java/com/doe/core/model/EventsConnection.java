package com.doe.core.model;

import java.net.Socket;
import java.util.UUID;

/**
 * Represents a long-lived events connection for a specific job executing inside a remote worker.
 * Allows pure signaling directly with the manager without going through the JVM process supervisor directly.
 */
public class EventsConnection {

    private final UUID workflowId;
    private final UUID jobId;
    private final Socket socket;

    public EventsConnection(UUID workflowId, UUID jobId, Socket socket) {
        if (workflowId == null) {
            throw new IllegalArgumentException("Workflow ID must not be null");
        }
        if (jobId == null) {
            throw new IllegalArgumentException("Job ID must not be null");
        }
        if (socket == null) {
            throw new IllegalArgumentException("Socket must not be null");
        }
        this.workflowId = workflowId;
        this.jobId = jobId;
        this.socket = socket;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public Socket getSocket() {
        return socket;
    }
}
