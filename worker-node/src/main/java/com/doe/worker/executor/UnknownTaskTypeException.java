package com.doe.worker.executor;

/**
 * Thrown by {@link TaskPluginRegistry} when a job payload's {@code "type"} field
 * does not match any registered plugin.
 *
 * <p>Callers (e.g. {@code WorkerClient}) should catch this specifically and map
 * it to a {@code FAILED} job result with a human-readable message, rather than
 * treating it as an unexpected runtime error.
 */
public class UnknownTaskTypeException extends RuntimeException {

    private final String type;

    public UnknownTaskTypeException(String type) {
        super("No plugin registered for task type: \"" + type + "\"");
        this.type = type;
    }

    /** The unrecognised type string extracted from the job payload. */
    public String getType() {
        return type;
    }
}
