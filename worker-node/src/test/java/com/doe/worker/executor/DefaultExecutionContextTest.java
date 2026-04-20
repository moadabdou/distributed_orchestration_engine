package com.doe.worker.executor;

import com.doe.core.executor.JobDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultExecutionContextTest {

    @Test
    @DisplayName("log() buffers messages and getBufferedLogs() returns them")
    void log_buffersMessages() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "type", "{}", 1000, 0);
        DefaultExecutionContext context = new DefaultExecutionContext(def, Collections.emptyMap(), Collections.emptyMap(), null);
        context.log("message 1");
        context.log("message 2");
        
        List<String> logs = context.getBufferedLogs();
        assertEquals(2, logs.size());
        assertEquals("message 1", logs.get(0));
        assertEquals("message 2", logs.get(1));
    }

    @Test
    @DisplayName("log() caps logs based on total size")
    void log_capsBySize() {
        // Limit to 20 characters
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "type", "{}", 1000, 0);
        DefaultExecutionContext context = new DefaultExecutionContext(
                def, Collections.emptyMap(), Collections.emptyMap(), null, 20);
        
        context.log("1234567890"); // 10 chars
        context.log("1234567890"); // 10 chars, total 20
        
        assertEquals(2, context.getBufferedLogs().size());
        assertEquals(20, context.getBufferedLogsSize());
        
        context.log("A"); // This should trigger truncation
        
        List<String> logs = context.getBufferedLogs();
        assertEquals(3, logs.size());
        assertTrue(logs.get(2).contains("TRUNCATED"));
        
        context.log("B"); // Should be ignored
        assertEquals(3, context.getBufferedLogs().size());
    }

    @Test
    @DisplayName("getBufferedLogsSize() returns correct cumulative size")
    void getBufferedLogsSize_returnsCorrectSize() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "type", "{}", 1000, 0);
        DefaultExecutionContext context = new DefaultExecutionContext(def, Collections.emptyMap(), Collections.emptyMap(), null);
        context.log("abc");
        context.log("defg");
        
        assertEquals(7, context.getBufferedLogsSize());
    }
}
