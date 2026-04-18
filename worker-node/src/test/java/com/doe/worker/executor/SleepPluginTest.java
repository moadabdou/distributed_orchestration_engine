package com.doe.worker.executor;

import com.doe.core.executor.JobDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SleepPluginTest {

    private final SleepPlugin plugin = new SleepPlugin();
    private final DefaultExecutionContext context = new DefaultExecutionContext();

    @Test
    @DisplayName("sleeps for the given ms")
    void execute_sleeps() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "sleep", "{\"ms\":10}");
        assertEquals("slept 10ms", plugin.execute(def, context));
    }

    @Test
    @DisplayName("negative ms throws IllegalArgumentException")
    void execute_negativeMs_throws() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "sleep", "{\"ms\":-1}");
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute(def, context));
    }

    @Test
    @DisplayName("missing 'ms' field throws IllegalArgumentException")
    void execute_missingMs_throws() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "sleep", "{}");
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute(def, context));
    }
}
