package com.doe.core.executor;

/**
 * SPI interface for task executors.
 * 
 * <p>Implementors interpret the JSON {@code payload} within {@link JobDefinition} 
 * and return a string result on success, or throw an exception on failure.
 */
public interface TaskExecutor {

    /**
     * @return the unique type identifier for this executor (e.g., "echo", "bash")
     */
    String getType();

    /**
     * Executes the given job definition within the provided context.
     *
     * @param definition task metadata and payload
     * @param context    execution environment and utilities
     * @return the result string to include in {@code JOB_RESULT}
     * @throws Exception if execution fails for any reason
     */
    String execute(ExecutionContext context) throws Exception;

    /**
     * Attempts to cancel the currently running job.
     * 
     * @throws Exception if cancellation fails
     */
    void cancel() throws Exception;

    /**
     * Validates the job definition before submission to a worker.
     * 
     * @param definition the definition to validate
     * @throws Exception if validation fails (e.g., missing required fields)
     */
    void validate(JobDefinition definition) throws Exception;
}
