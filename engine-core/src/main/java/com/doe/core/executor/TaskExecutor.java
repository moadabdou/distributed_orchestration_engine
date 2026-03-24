package com.doe.core.executor;

/**
 * Strategy interface for executing a job payload.
 *
 * <p>Implementors interpret the JSON {@code payload} string and return a
 * string result on success, or throw an exception on failure.
 */
public interface TaskExecutor {

    /**
     * Executes the given payload.
     *
     * @param payload JSON string describing the task to perform
     * @return the result string to include in {@code JOB_RESULT}
     * @throws Exception if execution fails for any reason
     */
    String execute(String payload) throws Exception;
}
