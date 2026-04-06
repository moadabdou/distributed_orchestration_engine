package com.doe.worker.executor;

import com.doe.core.executor.TaskExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Plugin for {@code "type": "sleep"} jobs.
 *
 * <p>Expected payload:
 * <pre>{ "type": "sleep", "ms": 500 }</pre>
 *
 * Sleeps for {@code ms} milliseconds and returns {@code "slept <N>ms"}.
 */
public class SleepPlugin implements TaskExecutor {

    private static final Gson GSON = new Gson();

    @Override
    public String execute(String payload) throws InterruptedException {
        JsonObject json = GSON.fromJson(payload, JsonObject.class);
        if (json == null || !json.has("ms")) {
            throw new IllegalArgumentException("sleep payload requires an 'ms' field");
        }
        long ms = json.get("ms").getAsLong();
        if (ms < 0) {
            throw new IllegalArgumentException("sleep 'ms' must be non-negative, got: " + ms);
        }
        Thread.sleep(ms);
        return "slept " + ms + "ms";
    }
}
