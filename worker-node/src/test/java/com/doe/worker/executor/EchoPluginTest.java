package com.doe.worker.executor;

import com.doe.core.executor.JobDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EchoPluginTest {

    private final EchoPlugin plugin = new EchoPlugin();
    private final DefaultExecutionContext context = new DefaultExecutionContext();

    @Test
    @DisplayName("returns the value of 'data' unchanged")
    void execute_returnsData() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "echo", "{\"data\":\"hello world\"}");
        assertEquals("hello world", plugin.execute(def, context));
    }

    @Test
    @DisplayName("empty string data is returned as-is")
    void execute_emptyData() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "echo", "{\"data\":\"\"}");
        assertEquals("", plugin.execute(def, context));
    }

    @Test
    @DisplayName("missing 'data' field throws IllegalArgumentException")
    void execute_missingData_throws() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "echo", "{}");
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute(def, context));
    }
}
