package com.doe.worker.executor;

import com.doe.core.executor.XComClient;
import com.doe.worker.client.WorkerClient;
import com.doe.worker.client.XComCorrelationRegistry;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of XComClient that uses the WorkerClient to communicate with the Manager via TCP.
 */
public class TcpXComClient implements XComClient {

    private static final Logger LOG = LoggerFactory.getLogger(TcpXComClient.class);

    private final WorkerClient workerClient;
    private final UUID workflowId;
    private final UUID jobId;
    private final long timeoutMs;

    public TcpXComClient(WorkerClient workerClient, UUID workflowId, UUID jobId, long timeoutMs) {
        this.workerClient = workerClient;
        this.workflowId = workflowId;
        this.jobId = jobId;
        this.timeoutMs = timeoutMs;
    }

    public WorkerClient getWorkerClient() {
        return workerClient;
    }

    @Override
    public void push(String key, String value, String type) {
        String correlationId = UUID.randomUUID().toString();
        JsonObject payload = new JsonObject();
        payload.addProperty("correlationId", correlationId);
        payload.addProperty("jobId", jobId.toString());
        payload.addProperty("workflowId", workflowId.toString());
        payload.addProperty("command", "push");
        payload.addProperty("key", key);
        payload.addProperty("value", value);
        payload.addProperty("type", type);

        workerClient.sendXComRequest(payload);
        LOG.info("TcpXComClient: sent XCOM push for job {}, key={}, correlationId={}", jobId, key, correlationId);
    }

    @Override
    public String pull(String key) {
        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<String> future = new CompletableFuture<>();
        XComCorrelationRegistry.getInstance().register(correlationId, future);

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("correlationId", correlationId);
            payload.addProperty("jobId", jobId.toString());
            payload.addProperty("workflowId", workflowId.toString());
            payload.addProperty("command", "pull");
            payload.addProperty("key", key);

            workerClient.sendXComRequest(payload);
            LOG.debug("XCom pull sent: key={}, correlationId={}", key, correlationId);

            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOG.error("XCom pull failed: key={}, correlationId={}", key, correlationId, e);
            return null;
        } finally {
            XComCorrelationRegistry.getInstance().remove(correlationId);
        }
    }
}
