package com.doe.worker.executor;

import com.doe.core.executor.TaskExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Plugin for {@code "type": "echo"} jobs.
 *
 * <p>Expected payload:
 * <pre>{ "type": "echo", "data": "hello" }</pre>
 *
 * Returns the value of {@code "data"} unchanged.
 */
public class EchoPlugin implements TaskExecutor {

    private static final Gson GSON = new Gson();

    @Override
    public String execute(String payload) {
        JsonObject json = GSON.fromJson(payload, JsonObject.class);
        if (json == null || !json.has("data")) {
            throw new IllegalArgumentException("echo payload requires a 'data' field");
        }
        return json.get("data").getAsString();
    }
}
