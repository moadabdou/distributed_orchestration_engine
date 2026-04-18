package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.JobDefinition;
import com.doe.core.executor.TaskExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Plugin for {@code "type": "echo"} jobs.
 *
 * <p>Expected payload:
 * <pre>{ "data": "hello" }</pre>
 *
 * Returns the value of {@code "data"} unchanged.
 */
public class EchoPlugin implements TaskExecutor {

    private static final Gson GSON = new Gson();

    @Override
    public String getType() {
        return "echo";
    }

    @Override
    public String execute(JobDefinition definition, ExecutionContext context) {
        JsonObject json = GSON.fromJson(definition.payload(), JsonObject.class);
        if (json == null || !json.has("data")) {
            throw new IllegalArgumentException("echo payload requires a 'data' field");
        }
        return json.get("data").getAsString();
    }

    @Override
    public void cancel() {
        // Echo is synchronous and fast, no special cancellation needed
    }

    @Override
    public void validate(JobDefinition definition) {
        JsonObject json = GSON.fromJson(definition.payload(), JsonObject.class);
        if (json == null || !json.has("data")) {
            throw new IllegalArgumentException("echo payload requires a 'data' field");
        }
    }
}
