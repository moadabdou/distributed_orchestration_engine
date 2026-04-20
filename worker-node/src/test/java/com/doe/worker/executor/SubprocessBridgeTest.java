package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.XComClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SubprocessBridgeTest {

    private Process process;
    private ExecutionContext context;
    private XComClient xComClient;
    private UUID jobId;
    private SubprocessBridge bridge;

    @BeforeEach
    void setUp() {
        process = mock(Process.class);
        context = mock(ExecutionContext.class);
        xComClient = mock(XComClient.class);
        jobId = UUID.randomUUID();
    }

    @Test
    void testXComPushDetection() throws Exception {
        String pushCommand = "__FERN_CMD__Xcom:{\"command\": \"push\", \"key\": \"test_key\", \"value\": \"test_value\", \"type\": \"message\"}\n";
        InputStream stdout = new ByteArrayInputStream(pushCommand.getBytes(StandardCharsets.UTF_8));
        InputStream stderr = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream stdin = new ByteArrayOutputStream();

        when(process.getInputStream()).thenReturn(stdout);
        when(process.getErrorStream()).thenReturn(stderr);
        when(process.getOutputStream()).thenReturn(stdin);
        when(process.isAlive()).thenReturn(true, false); // Alive then dead to stop the loop

        bridge = new SubprocessBridge(process, context, xComClient, jobId);
        
        // Run proxyStdout in the current thread for testing (avoiding virtual thread complexity)
        // Since proxyStdout is private, we'll use a hack or just test via the public start() and waiting.
        // For unit test, we can actually just call start() and sleep a bit, or refactor to make it testable.
        // Let's use start() and wait.
        bridge.start();

        // Wait for virtual threads to process the line
        TimeUnit.MILLISECONDS.sleep(200);

        verify(xComClient).push("test_key", "test_value", "message");
        
        // Verify ACK was written to stdin
        String stdinContent = stdin.toString(StandardCharsets.UTF_8);
        assertEquals("ACK\n", stdinContent);
        
        verify(context, never()).log(anyString()); // XCom command shouldn't be logged
    }

    @Test
    void testXComPullDetection() throws Exception {
        String pullCommand = "__FERN_CMD__Xcom:{\"command\": \"pull\", \"key\": \"query_key\"}\n";
        InputStream stdout = new ByteArrayInputStream(pullCommand.getBytes(StandardCharsets.UTF_8));
        InputStream stderr = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream stdin = new ByteArrayOutputStream();

        when(process.getInputStream()).thenReturn(stdout);
        when(process.getErrorStream()).thenReturn(stderr);
        when(process.getOutputStream()).thenReturn(stdin);
        when(process.isAlive()).thenReturn(true, false);
        when(xComClient.pull("query_key")).thenReturn("retrieved_value");

        bridge = new SubprocessBridge(process, context, xComClient, jobId);
        bridge.start();

        TimeUnit.MILLISECONDS.sleep(200);

        verify(xComClient).pull("query_key");
        
        // Verify result was written to stdin
        String stdinContent = stdin.toString(StandardCharsets.UTF_8);
        assertTrue(stdinContent.contains("\"value\":\"retrieved_value\""));
        assertTrue(stdinContent.contains("\"key\":\"query_key\""));
    }

    @Test
    void testNormalOutputLogging() throws Exception {
        String normalOutput = "Hello World\nError occurred\n";
        InputStream stdout = new ByteArrayInputStream(normalOutput.getBytes(StandardCharsets.UTF_8));
        InputStream stderr = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream stdin = new ByteArrayOutputStream();

        when(process.getInputStream()).thenReturn(stdout);
        when(process.getErrorStream()).thenReturn(stderr);
        when(process.getOutputStream()).thenReturn(stdin);
        when(process.isAlive()).thenReturn(true, false);

        bridge = new SubprocessBridge(process, context, xComClient, jobId);
        bridge.start();

        TimeUnit.MILLISECONDS.sleep(200);

        verify(context).log("Hello World");
        verify(context).log("Error occurred");
        verify(xComClient, never()).push(anyString(), anyString(), anyString());
    }
}
