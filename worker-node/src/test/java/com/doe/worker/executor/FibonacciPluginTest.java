package com.doe.worker.executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FibonacciPluginTest {

    private final FibonacciPlugin plugin = new FibonacciPlugin();

    @Test
    @DisplayName("fib(0) = 0")
    void fib_zero() throws Exception {
        assertEquals("0", plugin.execute("{\"type\":\"fibonacci\",\"n\":0}"));
    }

    @Test
    @DisplayName("fib(1) = 1")
    void fib_one() throws Exception {
        assertEquals("1", plugin.execute("{\"type\":\"fibonacci\",\"n\":1}"));
    }

    @Test
    @DisplayName("fib(10) = 55")
    void fib_ten() throws Exception {
        assertEquals("55", plugin.execute("{\"type\":\"fibonacci\",\"n\":10}"));
    }

    @Test
    @DisplayName("fib at max boundary (40) completes without error")
    void fib_maxBoundary() throws Exception {
        assertDoesNotThrow(() -> plugin.execute("{\"type\":\"fibonacci\",\"n\":40}"));
    }

    @Test
    @DisplayName("n > FIB_MAX throws IllegalArgumentException")
    void fib_overMax_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute("{\"type\":\"fibonacci\",\"n\":41}"));
    }

    @Test
    @DisplayName("negative n throws IllegalArgumentException")
    void fib_negative_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute("{\"type\":\"fibonacci\",\"n\":-1}"));
    }

    @Test
    @DisplayName("missing 'n' field throws IllegalArgumentException")
    void fib_missingN_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute("{\"type\":\"fibonacci\"}"));
    }
}
