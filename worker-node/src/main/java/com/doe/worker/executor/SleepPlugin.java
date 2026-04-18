package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.JobDefinition;
import com.doe.core.executor.TaskExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Plugin for {@code "type": "sleep"} jobs.
 *
 * <p>Expected payload:
 * <pre>{ "ms": 500 }</pre>
 *
 * Sleeps for {@code ms} milliseconds and returns {@code "slept <N>ms"}.
 */
public class SleepPlugin implements TaskExecutor {

    private static final Gson GSON = new Gson();

    @Override
    public String getType() {
        return "sleep";
    }

    @Override
    public String execute(JobDefinition definition, ExecutionContext context) throws InterruptedException {
        JsonObject json = GSON.fromJson(definition.payload(), JsonObject.class);
        if (json == null || !json.has("ms")) {
            throw new IllegalArgumentException("sleep payload requires an 'ms' field");
        }
        long ms = json.get("ms").getAsLong();
        if (ms < 0) {
            throw new IllegalArgumentException("sleep 'ms' must be non-negative, got: " + ms);
        }
        
        context.log("Plan: Sleep for " + ms + "ms");
        
        long remaining = ms;
        long interval = 500;
        
        while (remaining > 0) {
            long toSleep = Math.min(remaining, interval);
            context.log("Sleeping... remaining: " + remaining + "ms");
            Thread.sleep(toSleep);
            remaining -= toSleep;
        }
        
        context.log("Sleep finished.");
        return "slept " + ms + "ms";
    }

    @Override
    public void cancel() {
        // Thread.sleep handles InterruptedException when the thread is interrupted
    }

    @Override
    public void validate(JobDefinition definition) {
        JsonObject json = GSON.fromJson(definition.payload(), JsonObject.class);
        if (json == null || !json.has("ms")) {
            throw new IllegalArgumentException("sleep payload requires an 'ms' field");
        }
    }
}
