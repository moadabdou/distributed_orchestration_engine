package com.doe.worker.executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskPluginRegistryTest {

    private TaskPluginRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TaskPluginRegistry();
    }

    @Test
    @DisplayName("register and execute — delegates to the registered plugin")
    void dispatch_knownType() throws Exception {
        registry.register("ping", payload -> "pong");
        assertEquals("pong", registry.execute("{\"type\":\"ping\"}"));
    }

    @Test
    @DisplayName("execute with unregistered type — throws UnknownTaskTypeException")
    void dispatch_unknownType_throws() {
        registry.register("echo", payload -> payload);
        UnknownTaskTypeException ex = assertThrows(UnknownTaskTypeException.class,
                () -> registry.execute("{\"type\":\"nope\"}"));
        assertEquals("nope", ex.getType());
    }

    @Test
    @DisplayName("execute with missing 'type' field — throws IllegalArgumentException")
    void payload_missingType_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.execute("{\"data\":\"hello\"}"));
    }

    @Test
    @DisplayName("re-registering same key replaces the previous plugin")
    void lastRegistrationWins() throws Exception {
        registry.register("double", payload -> "first");
        registry.register("double", payload -> "second");
        assertEquals("second", registry.execute("{\"type\":\"double\"}"));
    }

    @Test
    @DisplayName("register with blank type — throws IllegalArgumentException")
    void register_blankType_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("  ", payload -> "x"));
    }

    @Test
    @DisplayName("register with null plugin — throws IllegalArgumentException")
    void register_nullPlugin_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("foo", null));
    }

    @Test
    @DisplayName("register returns the same registry instance for fluent chaining")
    void register_returnsThis() {
        assertSame(registry, registry.register("x", payload -> "y"));
    }
}
