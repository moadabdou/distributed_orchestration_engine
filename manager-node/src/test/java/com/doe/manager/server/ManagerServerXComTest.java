package com.doe.manager.server;

import com.doe.core.model.Job;
import com.doe.core.model.WorkerConnection;
import com.doe.core.protocol.Message;
import com.doe.core.registry.JobRegistry;
import com.doe.core.registry.WorkerRegistry;
import com.doe.manager.workflow.XComService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ManagerServerXComTest {

    private static final Gson GSON = new Gson();
    private XComService xComService;
    private JobRegistry jobRegistry;
    private ManagerServer server;
    private WorkerConnection connection;
    private Socket socket;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() throws Exception {
        xComService = mock(XComService.class);
        jobRegistry = mock(JobRegistry.class);
        
        // port, heartbeatCheck, heartbeatTimeout, jwtSecret, defaultCapacity, registry, jobRegistry, dagScheduler, jobScheduler, timeoutMonitor, eventListener, xComService, deathListeners
        server = new ManagerServer(9090, 5000, 15000, "my-super-secret-key-that-is-long-enough-for-hmac", 4, 
                mock(WorkerRegistry.class), jobRegistry, null, null, null, null, xComService, null);
        
        connection = mock(WorkerConnection.class);
        socket = mock(Socket.class);
        outputStream = new ByteArrayOutputStream();
        
        when(connection.getSocket()).thenReturn(socket);
        when(socket.getOutputStream()).thenReturn(outputStream);
    }

    @Test
    void testHandlePushRequest() {
        UUID workerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        String correlationId = "corr-123";

        JsonObject payload = new JsonObject();
        payload.addProperty("command", "push");
        payload.addProperty("correlationId", correlationId);
        payload.addProperty("jobId", jobId.toString());
        payload.addProperty("workflowId", workflowId.toString());
        payload.addProperty("key", "myKey");
        payload.addProperty("value", "myValue");
        payload.addProperty("type", "myType");

        Message message = mock(Message.class);
        when(message.payloadAsString()).thenReturn(GSON.toJson(payload));

        Job job = mock(Job.class);
        when(jobRegistry.get(jobId)).thenReturn(Optional.of(job));

        server.handleXComRequest(workerId, connection, message);

        verify(xComService).push(workflowId, jobId, "myKey", "myValue", "myType");
        
        String responseJson = getResponseJson();
        assertTrue(responseJson.contains("\"status\":\"SUCCESS\""));
        assertTrue(responseJson.contains("\"correlationId\":\"corr-123\""));
    }

    @Test
    void testHandlePullRequest() {
        UUID workerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        String correlationId = "corr-456";

        JsonObject payload = new JsonObject();
        payload.addProperty("command", "pull");
        payload.addProperty("correlationId", correlationId);
        payload.addProperty("jobId", jobId.toString());
        payload.addProperty("workflowId", workflowId.toString());
        payload.addProperty("key", "queryKey");

        Message message = mock(Message.class);
        when(message.payloadAsString()).thenReturn(GSON.toJson(payload));

        Job job = mock(Job.class);
        when(jobRegistry.get(jobId)).thenReturn(Optional.of(job));
        when(xComService.pull(workflowId, "queryKey")).thenReturn(Optional.of("foundValue"));

        server.handleXComRequest(workerId, connection, message);

        String responseJson = getResponseJson();
        assertTrue(responseJson.contains("\"status\":\"SUCCESS\""));
        assertTrue(responseJson.contains("\"value\":\"foundValue\""));
    }

    @Test
    void testHandlePullRequestNotFound() {
        UUID workerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        
        JsonObject payload = new JsonObject();
        payload.addProperty("command", "pull");
        payload.addProperty("correlationId", "corr-789");
        payload.addProperty("jobId", jobId.toString());
        payload.addProperty("workflowId", workflowId.toString());
        payload.addProperty("key", "missingKey");

        Message message = mock(Message.class);
        when(message.payloadAsString()).thenReturn(GSON.toJson(payload));

        Job job = mock(Job.class);
        when(jobRegistry.get(jobId)).thenReturn(Optional.of(job));
        when(xComService.pull(workflowId, "missingKey")).thenReturn(Optional.empty());

        server.handleXComRequest(workerId, connection, message);

        String responseJson = getResponseJson();
        assertTrue(responseJson.contains("\"status\":\"NOT_FOUND\""));
    }

    private String getResponseJson() {
        byte[] bytes = outputStream.toByteArray();
        // Skip protocol header (5 bytes: 1 type + 4 length)
        if (bytes.length < 5) return "";
        return new String(bytes, 5, bytes.length - 5);
    }
}
