package com.doe.worker.executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShellScriptPluginTest {

    private final ShellScriptPlugin plugin = new ShellScriptPlugin();

    @Test
    @DisplayName("simple echo script returns its output")
    void execute_echo_returnsOutput() throws Exception {
        String result = plugin.execute("{\"type\":\"bash\",\"script\":\"echo hello\"}");
        assertTrue(result.contains("hello"), "Expected output to contain 'hello', got: " + result);
    }

    @Test
    @DisplayName("stdout and stderr are both captured (redirectErrorStream)")
    void execute_stderrMerged() throws Exception {
        String result = plugin.execute(
                "{\"type\":\"bash\",\"script\":\"echo stdout; echo stderr >&2\"}");
        assertTrue(result.contains("stdout"), "Missing stdout in: " + result);
        assertTrue(result.contains("stderr"), "Missing stderr in: " + result);
    }

    @Test
    @DisplayName("non-zero exit code throws an exception with exit code in message")
    void execute_nonzeroExit_throws() {
        Exception ex = assertThrows(Exception.class,
                () -> plugin.execute("{\"type\":\"bash\",\"script\":\"exit 42\"}"));
        assertTrue(ex.getMessage().contains("42"),
                "Expected exit code 42 in message: " + ex.getMessage());
    }

    @Test
    @DisplayName("script exceeding timeoutMs is killed and throws")
    void execute_timeout_kills() {
        // Set a very short plugin-level timeout; the outer CompletableFuture timeout is not involved
        // because ShellScriptPlugin enforces its own Process.waitFor deadline first.
        String payload = "{\"type\":\"bash\",\"script\":\"sleep 60\",\"timeoutMs\":200}";
        Exception ex = assertThrows(Exception.class,
                () -> plugin.execute(payload));
        assertTrue(ex.getMessage().toLowerCase().contains("timeout") ||
                        ex.getMessage().toLowerCase().contains("killed"),
                "Expected timeout/killed message, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("zero or negative timeoutMs throws IllegalArgumentException before process starts")
    void execute_invalidTimeout_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute("{\"type\":\"bash\",\"script\":\"echo hi\",\"timeoutMs\":0}"));
    }

    @Test
    @DisplayName("missing 'script' field throws IllegalArgumentException")
    void execute_missingScript_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> plugin.execute("{\"type\":\"bash\"}"));
    }

    @Test
    @DisplayName("output exceeding 64 KB cap is truncated with notice")
    void execute_outputTruncated() throws Exception {
        // Build the payload with Gson to avoid multi-level escaping issues in string literals
        com.google.gson.JsonObject p = new com.google.gson.JsonObject();
        p.addProperty("type", "bash");
        // Generate ~200 KB: 1000 lines of 200 'A' chars each
        p.addProperty("script", "python3 -c \"import sys; [sys.stdout.write('A'*200+'\\n') for _ in range(1000)]\"");
        String result = plugin.execute(new com.google.gson.Gson().toJson(p));

        assertTrue(result.contains("truncated"),
                "Expected truncation notice in output (length=" + result.length() + ")");
        assertTrue(result.length() <= ShellScriptPlugin.MAX_OUTPUT_BYTES + 100,
                "Result length " + result.length() + " exceeds expected cap");
    }
}
