package com.doe.worker.executor;

import com.doe.core.executor.TaskExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Plugin for {@code "type": "fibonacci"} jobs.
 *
 * <p>Expected payload:
 * <pre>{ "type": "fibonacci", "n": 10 }</pre>
 *
 * Computes the Nth Fibonacci number iteratively and returns it as a string.
 * Input is capped at {@value #FIB_MAX} to prevent absurdly long computations.
 */
public class FibonacciPlugin implements TaskExecutor {

    private static final Gson GSON = new Gson();

    /** Hard cap on Fibonacci input. */
    public static final int FIB_MAX = 40;

    @Override
    public String execute(String payload) {
        JsonObject json = GSON.fromJson(payload, JsonObject.class);
        if (json == null || !json.has("n")) {
            throw new IllegalArgumentException("fibonacci payload requires an 'n' field");
        }
        int n = json.get("n").getAsInt();
        if (n < 0) {
            throw new IllegalArgumentException("fibonacci 'n' must be non-negative, got: " + n);
        }
        if (n > FIB_MAX) {
            throw new IllegalArgumentException(
                    "fibonacci 'n' must be \u2264 " + FIB_MAX + " to prevent excessive computation, got: " + n);
        }
        return String.valueOf(fib(n));
    }

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
