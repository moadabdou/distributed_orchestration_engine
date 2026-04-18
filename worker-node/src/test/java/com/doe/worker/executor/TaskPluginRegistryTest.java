package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.JobDefinition;
import com.doe.core.executor.TaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaskPluginRegistryTest {

    private TaskPluginRegistry registry;
    private final ExecutionContext context = new DefaultExecutionContext();

    @BeforeEach
    void setUp() {
        registry = new TaskPluginRegistry();
    }

    @Test
    @DisplayName("discovered plugins via SPI are present")
    void spiDiscovery() {
        assertTrue(registry.getExecutor("echo").isPresent());
        assertTrue(registry.getExecutor("bash").isPresent());
        assertTrue(registry.getExecutor("sleep").isPresent());
        assertTrue(registry.getExecutor("fibonacci").isPresent());
    }

    @Test
    @DisplayName("execute delegates to the correct plugin")
    void execute_delegates() throws Exception {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "echo", "{\"data\":\"ping\"}");
        assertEquals("ping", registry.execute(def, context));
    }

    @Test
    @DisplayName("execute with unknown type throws UnknownTaskTypeException")
    void unknownType_throws() {
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "nope", "{}");
        assertThrows(UnknownTaskTypeException.class, () -> registry.execute(def, context));
    }

    @Test
    @DisplayName("manual registration works")
    void manualRegistration() throws Exception {
        TaskExecutor mockExecutor = new EchoPlugin(); // Or a custom mock
        registry.register("mock", mockExecutor);
        
        JobDefinition def = new JobDefinition(UUID.randomUUID(), "mock", "{\"data\":\"pong\"}");
        assertEquals("pong", registry.execute(def, context));
    }
}
