package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.JobDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SleepPluginTest {

    private final SleepPlugin plugin = new SleepPlugin();
    @Test
    @DisplayName("sleeps for the given ms")
    void execute_sleeps() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "sleep", "{\"ms\":10}", 10000, 0, null);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        assertEquals("slept 10ms", plugin.execute(context));
    }

    @Test
    @DisplayName("negative ms throws IllegalArgumentException")
    void execute_negativeMs_throws() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "sleep", "{\"ms\":-1}", 10000, 0, null);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute(context));
    }

    @Test
    @DisplayName("missing 'ms' field throws IllegalArgumentException")
    void execute_missingMs_throws() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "sleep", "{}", 10000, 0, null);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute(context));
    }
}
