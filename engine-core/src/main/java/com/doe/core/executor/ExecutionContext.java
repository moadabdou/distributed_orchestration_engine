package com.doe.core.executor;

import java.util.List;
import java.util.Map;

/**
 * Context provided to an executor during task execution.
 * Contains environment variables, secrets, XCom storage access, and logging utilities.
 */
public interface ExecutionContext {
    /**
     * @return the full job definition being executed
     */
    JobDefinition getDefinition();

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

    /**
     * @return the list of logs collected during execution
     */
    List<String> getBufferedLogs();

    /**
     * @return the cumulative size of buffered logs in characters
     */
    long getBufferedLogsSize();
    
    /**
     * Sets a listener to be notified of each log message as it is produced.
     * 
     * @param listener a consumer that receives each log message
     */
    void setLogListener(java.util.function.Consumer<String> listener);
}
