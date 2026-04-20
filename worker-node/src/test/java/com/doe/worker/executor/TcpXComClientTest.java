package com.doe.worker.executor;

import com.doe.worker.client.WorkerClient;
import com.doe.worker.client.XComCorrelationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class TcpXComClientTest {

    private WorkerClient workerClient;
    private UUID workflowId;
    private UUID jobId;
    private TcpXComClient xComClient;

    @BeforeEach
    void setUp() {
        workerClient = mock(WorkerClient.class);
        workflowId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        xComClient = new TcpXComClient(workerClient, workflowId, jobId, 1000);
    }

    @Test
    void testPushFormatsCorrectJson() {
        xComClient.push("key1", "val1", "type1");

        ArgumentCaptor<com.google.gson.JsonObject> payloadCaptor = ArgumentCaptor.forClass(com.google.gson.JsonObject.class);
        verify(workerClient).sendXComRequest(payloadCaptor.capture());

        com.google.gson.JsonObject payload = payloadCaptor.getValue();
        assertEquals("push", payload.get("command").getAsString());
        assertEquals("key1", payload.get("key").getAsString());
        assertEquals("val1", payload.get("value").getAsString());
        assertEquals("type1", payload.get("type").getAsString());
        assertEquals(workflowId.toString(), payload.get("workflowId").getAsString());
    }

    @Test
    void testPullRegistersCorrelationAndBlocks() throws Exception {
        // We'll trigger pull in a separate thread because it blocks
        CompletableFuture<String> pullResult = CompletableFuture.supplyAsync(() -> xComClient.pull("key1"));

        // Wait for it to send request and register
        TimeUnit.MILLISECONDS.sleep(100);
        
        ArgumentCaptor<com.google.gson.JsonObject> payloadCaptor = ArgumentCaptor.forClass(com.google.gson.JsonObject.class);
        verify(workerClient).sendXComRequest(payloadCaptor.capture());
        com.google.gson.JsonObject payload = payloadCaptor.getValue();
        assertEquals("pull", payload.get("command").getAsString());
        
        // Extract correlationId from payload
        String correlationId = payload.get("correlationId").getAsString();
        
        // Complete the future in the registry
        XComCorrelationRegistry.getInstance().complete(correlationId, "retrieved_val");
        
        assertEquals("retrieved_val", pullResult.get(1, TimeUnit.SECONDS));
    }

    @Test
    void testPullTimeout() throws Exception {
        // Use a very short timeout for this test
        xComClient = new TcpXComClient(workerClient, workflowId, jobId, 100);
        
        long start = System.currentTimeMillis();
        String result = xComClient.pull("non_existent");
        long duration = System.currentTimeMillis() - start;
        
        assertNull(result);
        assertTrue(duration >= 100, "Should have blocked for at least 100ms");
    }
    
    private void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
