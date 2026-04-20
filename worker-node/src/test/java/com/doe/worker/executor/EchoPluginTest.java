package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.JobDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EchoPluginTest {

    private final EchoPlugin plugin = new EchoPlugin();
    @Test
    @DisplayName("returns the value of 'data' unchanged")
    void execute_returnsData() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "echo", "{\"data\":\"hello world\"}", 10000, 0);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        assertEquals("hello world", plugin.execute(context));
    }

    @Test
    @DisplayName("empty string data is returned as-is")
    void execute_emptyData() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "echo", "{\"data\":\"\"}", 10000, 0);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        assertEquals("", plugin.execute(context));
    }

    @Test
    @DisplayName("missing 'data' field throws IllegalArgumentException")
    void execute_missingData_throws() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "echo", "{}", 10000, 0);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute(context));
    }
}
