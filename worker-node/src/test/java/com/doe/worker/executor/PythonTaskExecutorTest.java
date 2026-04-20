package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.JobDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PythonTaskExecutorTest {

    private final PythonTaskExecutor plugin = new PythonTaskExecutor();
    @Test
    @DisplayName("executes python script and returns success message")
    void execute_script() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "python", "{\"script\":\"print('hello python')\"}", 10000, 0);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        String result = plugin.execute(context);
        assertTrue(result.startsWith("Executed successfully in"));
        assertTrue(context.getBufferedLogs().contains("hello python"));
    }

    @Test
    @DisplayName("executes python script with arguments")
    void execute_with_args() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "python", 
                "{\"script\":\"import sys; print(sys.argv[1])\", \"args\": [\"test_arg\"]}", 10000, 0);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        String result = plugin.execute(context);
        assertTrue(result.startsWith("Executed successfully in"));
        assertTrue(context.getBufferedLogs().contains("test_arg"));
    }

    @Test
    @DisplayName("streams output to context logs")
    void streams_output() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "python", 
                "{\"script\":\"print('line1'); print('line2')\"}", 10000, 0);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        plugin.execute(context);
        
        assertTrue(context.getBufferedLogs().contains("line1"));
        assertTrue(context.getBufferedLogs().contains("line2"));
    }

    @Test
    @DisplayName("script failure throws Exception")
    void execute_failure() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "python", "{\"script\":\"import sys; sys.exit(1)\"}", 10000, 0);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        assertThrows(IllegalStateException.class, () -> plugin.execute(context));
    }

    @Test
    @DisplayName("invalid payload throws Exception")
    void validate_invalid() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "python", "{}", 10000, 0);
        assertThrows(IllegalArgumentException.class, () -> plugin.validate(def));
    }

    @Test
    @DisplayName("cancel kills the process")
    void cancel_killsProcess() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "python", 
                "{\"script\":\"import time; time.sleep(10)\"}", 10000, 0);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        
        Thread t = new Thread(() -> {
            try {
                plugin.execute(context);
            } catch (Exception ignored) {}
        });
        t.start();
        Thread.sleep(500); // Wait for process to start
        
        plugin.cancel();
        t.join(1000);
        assertFalse(t.isAlive());
    }
}
