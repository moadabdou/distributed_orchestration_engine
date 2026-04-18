package com.doe.worker.executor;

import com.doe.core.executor.JobDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShellScriptPluginTest {

    private final ShellScriptPlugin plugin = new ShellScriptPlugin();
    private final DefaultExecutionContext context = new DefaultExecutionContext();

    @Test
    @DisplayName("executes script and returns output")
    void execute_script() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "bash", "{\"script\":\"echo hello\"}");
        assertEquals("hello\n", plugin.execute(def, context));
    }

    @Test
    @DisplayName("script failure throws Exception")
    void execute_failure() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "bash", "{\"script\":\"exit 1\"}");
        assertThrows(Exception.class, () -> plugin.execute(def, context));
    }

    @Test
    @DisplayName("cancel kills the process")
    void cancel_killsProcess() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "bash", "{\"script\":\"sleep 10\"}");
        
        Thread t = new Thread(() -> {
            try {
                plugin.execute(def, context);
            } catch (Exception ignored) {}
        });
        t.start();
        Thread.sleep(100); // Wait for process to start
        
        plugin.cancel();
        t.join(500);
        assertFalse(t.isAlive());
    }
}
