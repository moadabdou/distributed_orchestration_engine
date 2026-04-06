package com.doe.worker.executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EchoPluginTest {

    private final EchoPlugin plugin = new EchoPlugin();

    @Test
    @DisplayName("returns the value of 'data' unchanged")
    void execute_returnsData() throws Exception {
        assertEquals("hello world", plugin.execute("{\"type\":\"echo\",\"data\":\"hello world\"}"));
    }

    @Test
    @DisplayName("empty string data is returned as-is")
    void execute_emptyData() throws Exception {
        assertEquals("", plugin.execute("{\"type\":\"echo\",\"data\":\"\"}"));
    }

    @Test
    @DisplayName("missing 'data' field throws IllegalArgumentException")
    void execute_missingData_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute("{\"type\":\"echo\"}"));
    }
}
