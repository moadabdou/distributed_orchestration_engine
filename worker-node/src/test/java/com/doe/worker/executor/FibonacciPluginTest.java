package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.JobDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FibonacciPluginTest {

    private final FibonacciPlugin plugin = new FibonacciPlugin();
    @Test
    @DisplayName("computes Nth Fibonacci number")
    void execute_computesFib() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "fibonacci", "{\"n\":10}", 10000, 0, null);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        assertEquals("55", plugin.execute(context));
    }

    @Test
    @DisplayName("n=0 returns 0")
    void execute_zero() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "fibonacci", "{\"n\":0}", 10000, 0, null);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        assertEquals("0", plugin.execute(context));
    }

    @Test
    @DisplayName("excessive n throws IllegalArgumentException")
    void execute_limit_throws() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "fibonacci", "{\"n\":100}", 10000, 0, null);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute(context));
    }
}
