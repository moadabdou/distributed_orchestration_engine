package com.doe.worker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Main} CLI argument parsing.
 */
class MainTest {

    // ──── parseArg ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("parseArg returns the value after the matching flag")
    void parseArg_returnsValueForKnownFlag() {
        String[] args = {"--host", "192.168.1.10", "--port", "8080"};
        assertEquals("192.168.1.10", Main.parseArg(args, "--host", "localhost"));
    }

    @Test
    @DisplayName("parseArg returns defaultValue when flag is absent")
    void parseArg_returnsDefaultWhenFlagAbsent() {
        String[] args = {"--port", "8080"};
        assertEquals("localhost", Main.parseArg(args, "--host", "localhost"));
    }

    @Test
    @DisplayName("parseArg returns defaultValue for empty args array")
    void parseArg_returnsDefaultForEmptyArgs() {
        assertEquals("localhost", Main.parseArg(new String[0], "--host", "localhost"));
    }

    @Test
    @DisplayName("parseArg ignores a flag that appears as the last element (no value follows)")
    void parseArg_ignoresFlagWithNoFollowingValue() {
        // --host is the last element, so there is no value after it → default
        String[] args = {"--port", "8080", "--host"};
        assertEquals("localhost", Main.parseArg(args, "--host", "localhost"));
    }

    // ──── parsePort ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("parsePort returns port value from --port flag")
    void parsePort_returnsPortForKnownFlag() {
        String[] args = {"--host", "localhost", "--port", "9999"};
        assertEquals(9999, Main.parsePort(args));
    }

    @Test
    @DisplayName("parsePort returns 9090 when --port flag is absent")
    void parsePort_returnsDefaultWhenFlagAbsent() {
        String[] args = {"--host", "some-manager"};
        assertEquals(9090, Main.parsePort(args));
    }

    @Test
    @DisplayName("parsePort returns 9090 for empty args array")
    void parsePort_returnsDefaultForEmptyArgs() {
        assertEquals(9090, Main.parsePort(new String[0]));
    }

    // ──── WorkerClient constructor validation ───────────────────────────────

    @Test
    @DisplayName("WorkerClient rejects null host")
    void workerClient_rejectsNullHost() {
        assertThrows(IllegalArgumentException.class,
                () -> new com.doe.worker.client.WorkerClient(null, 9090));
    }

    @Test
    @DisplayName("WorkerClient rejects blank host")
    void workerClient_rejectsBlankHost() {
        assertThrows(IllegalArgumentException.class,
                () -> new com.doe.worker.client.WorkerClient("  ", 9090));
    }

    @Test
    @DisplayName("WorkerClient rejects negative port")
    void workerClient_rejectsNegativePort() {
        assertThrows(IllegalArgumentException.class,
                () -> new com.doe.worker.client.WorkerClient("localhost", -1));
    }

    @Test
    @DisplayName("WorkerClient rejects port > 65535")
    void workerClient_rejectsPortAboveMax() {
        assertThrows(IllegalArgumentException.class,
                () -> new com.doe.worker.client.WorkerClient("localhost", 65536));
    }

    @Test
    @DisplayName("WorkerClient accepts port 0 (OS-assigned)")
    void workerClient_acceptsPortZero() {
        assertDoesNotThrow(() -> new com.doe.worker.client.WorkerClient("localhost", 0));
    }
}
