package com.doe.worker.executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SleepPluginTest {

    private final SleepPlugin plugin = new SleepPlugin();

    @Test
    @DisplayName("sleeps for the requested duration and returns expected string")
    void execute_sleeps() throws Exception {
        long before = System.currentTimeMillis();
        String result = plugin.execute("{\"type\":\"sleep\",\"ms\":50}");
        long elapsed = System.currentTimeMillis() - before;

        assertEquals("slept 50ms", result);
        assertTrue(elapsed >= 40, "Expected at least 40ms elapsed, got: " + elapsed);
    }

    @Test
    @DisplayName("ms=0 returns immediately")
    void execute_zeroMs() throws Exception {
        assertEquals("slept 0ms", plugin.execute("{\"type\":\"sleep\",\"ms\":0}"));
    }

    @Test
    @DisplayName("negative ms throws IllegalArgumentException")
    void execute_negativeMs_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute("{\"type\":\"sleep\",\"ms\":-1}"));
    }

    @Test
    @DisplayName("missing 'ms' field throws IllegalArgumentException")
    void execute_missingMs_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute("{\"type\":\"sleep\"}"));
    }
}
