package com.doe.worker.executor;

import com.doe.core.executor.JobDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FibonacciPluginTest {

    private final FibonacciPlugin plugin = new FibonacciPlugin();
    private final DefaultExecutionContext context = new DefaultExecutionContext();

    @Test
    @DisplayName("computes Nth Fibonacci number")
    void execute_computesFib() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "fibonacci", "{\"n\":10}");
        assertEquals("55", plugin.execute(def, context));
    }

    @Test
    @DisplayName("n=0 returns 0")
    void execute_zero() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "fibonacci", "{\"n\":0}");
        assertEquals("0", plugin.execute(def, context));
    }

    @Test
    @DisplayName("excessive n throws IllegalArgumentException")
    void execute_limit_throws() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "fibonacci", "{\"n\":100}");
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute(def, context));
    }
}
