package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.XComClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * Default implementation of {@link ExecutionContext} used by the worker.
 */
public class DefaultExecutionContext implements ExecutionContext {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultExecutionContext.class);

    private final Map<String, String> envVars;
    private final Map<String, String> secrets;
    private final XComClient xComClient;

    public DefaultExecutionContext() {
        this(Collections.emptyMap(), Collections.emptyMap(), new NoOpXComClient());
    }

    public DefaultExecutionContext(Map<String, String> envVars, Map<String, String> secrets, XComClient xComClient) {
        this.envVars = envVars;
        this.secrets = secrets;
        this.xComClient = xComClient;
    }

    @Override
    public Map<String, String> getEnvVars() {
        return envVars;
    }

    @Override
    public Map<String, String> getSecrets() {
        return secrets;
    }

    @Override
    public XComClient getXComClient() {
        return xComClient;
    }

    @Override
    public void log(String message) {
        LOG.info("[TASK-LOG] {}", message);
    }

    /**
     * Minimal XCom client that currently does nothing (placeholder for Issue 048).
     */
    private static class NoOpXComClient implements XComClient {
        @Override
        public void push(String key, String value) {
            LOG.debug("XCom push (no-op): {} = {}", key, value);
        }

        @Override
        public String pull(String key) {
            LOG.debug("XCom pull (no-op): {}", key);
            return null;
        }
    }
}
