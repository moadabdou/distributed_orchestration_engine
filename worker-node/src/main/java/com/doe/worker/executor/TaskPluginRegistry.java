package com.doe.worker.executor;

import com.doe.core.executor.TaskExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A thread-safe registry that maps task-type strings to {@link TaskExecutor} plugins
 * and dispatches job execution to the appropriate plugin.
 *
 * <h2>Usage</h2>
 * <pre>
 * TaskPluginRegistry registry = new TaskPluginRegistry();
 * registry.register("echo", new EchoPlugin());
 * registry.register("bash", new ShellScriptPlugin());
 *
 * String result = registry.execute("{\"type\":\"echo\",\"data\":\"hi\"}");
 * </pre>
 *
 * <h2>Type selection</h2>
 * The {@code "type"} field is intentionally extracted <em>only at execution time</em>,
 * keeping the payload opaque to the manager, transport, and database layers.
 * This allows the schema and protocol to remain type-agnostic.
 */
public class TaskPluginRegistry {

    private static final Gson GSON = new Gson();

    private final ConcurrentMap<String, TaskExecutor> plugins = new ConcurrentHashMap<>();

    /**
     * Registers a plugin for the given type key.
     * If a plugin was already registered for that key, it is replaced.
     *
     * @param type   the value of the {@code "type"} field in job payloads
     * @param plugin the executor to invoke for jobs of that type
     * @return this registry (for fluent chaining)
     */
    public TaskPluginRegistry register(String type, TaskExecutor plugin) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        if (plugin == null) {
            throw new IllegalArgumentException("plugin must not be null");
        }
        plugins.put(type, plugin);
        return this;
    }

    /**
     * Executes the job described by {@code payloadJson} using the plugin registered
     * for the payload's {@code "type"} field.
     *
     * @param payloadJson a JSON object containing at least a {@code "type"} field
     * @return the result string produced by the plugin
     * @throws IllegalArgumentException  if the payload is not valid JSON or has no {@code "type"} field
     * @throws UnknownTaskTypeException  if no plugin is registered for the payload's type
     * @throws Exception                 if the plugin itself throws
     */
    public String execute(String payloadJson) throws Exception {
        JsonObject json = GSON.fromJson(payloadJson, JsonObject.class);

        if (json == null || !json.has("type")) {
            throw new IllegalArgumentException(
                    "Job payload must be a JSON object with a 'type' field; got: " + payloadJson);
        }

        String type = json.get("type").getAsString();
        TaskExecutor plugin = plugins.get(type);

        if (plugin == null) {
            throw new UnknownTaskTypeException(type);
        }

        return plugin.execute(payloadJson);
    }
}
