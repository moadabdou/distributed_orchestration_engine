package com.doe.core.executor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * A pluggable {@link TaskExecutor} that interprets a JSON payload and runs
 * simple dummy work for testing the end-to-end job execution pipeline.
 *
 * <p>Supported payload shapes:
 * <pre>
 * { "type": "echo",      "data": "hello" }           → returns "hello"
 * { "type": "sleep",     "ms":   500     }           → sleeps N ms, returns "slept 500ms"
 * { "type": "fibonacci", "n":    10      }           → returns fibonacci(n) as string
 * </pre>
 *
 * Unknown types throw {@link IllegalArgumentException}.
 */
public class DummyTaskExecutor implements TaskExecutor {

    private static final Gson GSON = new Gson();

    /** Hard cap on fibonacci input to prevent absurdly long computations. */
    private static final int FIB_MAX = 40;

    @Override
    public String execute(String payload) throws Exception {
        JsonObject json = GSON.fromJson(payload, JsonObject.class);

        if (json == null || !json.has("type")) {
            throw new IllegalArgumentException("Payload must be a JSON object with a 'type' field");
        }

        String type = json.get("type").getAsString();

        return switch (type) {
            case "echo"      -> executeEcho(json);
            case "sleep"     -> executeSleep(json);
            case "fibonacci" -> executeFibonacci(json);
            default          -> throw new IllegalArgumentException("Unknown task type: " + type);
        };
    }

    // ──── Task implementations ────────────────────────────────────────────────

    private String executeEcho(JsonObject json) {
        if (!json.has("data")) {
            throw new IllegalArgumentException("echo task requires 'data' field");
        }
        return json.get("data").getAsString();
    }

    private String executeSleep(JsonObject json) throws InterruptedException {
        if (!json.has("ms")) {
            throw new IllegalArgumentException("sleep task requires 'ms' field");
        }
        long ms = json.get("ms").getAsLong();
        if (ms < 0) {
            throw new IllegalArgumentException("sleep 'ms' must be non-negative, got: " + ms);
        }
        Thread.sleep(ms);
        return "slept " + ms + "ms";
    }

    private String executeFibonacci(JsonObject json) {
        if (!json.has("n")) {
            throw new IllegalArgumentException("fibonacci task requires 'n' field");
        }
        int n = json.get("n").getAsInt();
        if (n < 0) {
            throw new IllegalArgumentException("fibonacci 'n' must be non-negative, got: " + n);
        }
        if (n > FIB_MAX) {
            throw new IllegalArgumentException(
                    "fibonacci 'n' must be ≤ " + FIB_MAX + " to prevent excessive computation, got: " + n);
        }
        return String.valueOf(fib(n));
    }

    // ──── Helpers ─────────────────────────────────────────────────────────────

    private static long fib(int n) {
        if (n <= 1) return n;
        long a = 0, b = 1;
        for (int i = 2; i <= n; i++) {
            long c = a + b;
            a = b;
            b = c;
        }
        return b;
    }
}
