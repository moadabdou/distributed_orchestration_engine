package com.doe.core.executor;

/**
 * Interface for pushing and pulling cross-job communication data (XComs).
 * Implementation will be provided by the engine to allow jobs to exchange data.
 */
public interface XComClient {
    /**
     * Pushes a value to the XCom storage for the current DAG run.
     * 
     * @param key   the key for the XCom value
     * @param value the value to store (should be JSON-serializable)
     * @param type  the type of the XCom (e.g., "message", "reference")
     */
    void push(String key, String value, String type);

    /**
     * Pushes a value with default type "message".
     */
    default void push(String key, String value) {
        push(key, value, "message");
    }

    /**
     * Pulls an XCom value by key from the current DAG run.
     * 
     * @param key the key to look up
     * @return the XCom value, or null if not found
     */
    String pull(String key);
}
