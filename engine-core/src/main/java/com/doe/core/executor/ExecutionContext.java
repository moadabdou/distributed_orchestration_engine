package com.doe.core.executor;

import java.util.Map;

/**
 * Context provided to an executor during task execution.
 * Contains environment variables, secrets, XCom storage access, and logging utilities.
 */
public interface ExecutionContext {
    /**
     * @return a map of environment variables available to the task
     */
    Map<String, String> getEnvVars();

    /**
     * @return a map of secrets (e.g., API keys, passwords) available to the task
     */
    Map<String, String> getSecrets();

    /**
     * @return the XCom client for cross-job data exchange
     */
    XComClient getXComClient();

    /**
     * Logs a message to the task-specific log stream.
     * 
     * @param message the message to log
     */
    void log(String message);
}
