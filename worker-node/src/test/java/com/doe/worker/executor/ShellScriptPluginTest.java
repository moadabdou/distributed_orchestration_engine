package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.JobDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShellScriptPluginTest {

    private final ShellScriptPlugin plugin = new ShellScriptPlugin();
    @Test
    @DisplayName("executes script and returns output")
    void execute_script() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "bash", "{\"script\":\"echo hello\"}", 10000, 0);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        assertTrue(plugin.execute(context).contains("successfully"));
    }

    @Test
    @DisplayName("script failure throws Exception")
    void execute_failure() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "bash", "{\"script\":\"exit 1\"}", 10000, 0);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        assertThrows(Exception.class, () -> plugin.execute(context));
    }

    @Test
    @DisplayName("cancel kills the process")
    void cancel_killsProcess() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), null, "test", "bash", "{\"script\":\"sleep 10\"}", 10000, 0);
        ExecutionContext context = new DefaultExecutionContext(def, null, null, null);
        
        Thread t = new Thread(() -> {
            try {
                plugin.execute(context);
            } catch (Exception ignored) {}
        });
        t.start();
        Thread.sleep(500); // Wait for process to start
        
        plugin.cancel();
        t.join(1000);
        assertFalse(t.isAlive());
    }
}
