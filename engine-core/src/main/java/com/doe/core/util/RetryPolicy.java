package com.doe.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;

/**
 * Reusable exponential-backoff retry utility.
 * <p>
 * Retries the supplied {@link ConnectTask} on {@link ConnectException} with
 * exponentially increasing delays, capped at {@code maxDelayMs}.
 * Other {@link IOException} subtypes are propagated immediately — only
 * connection failures trigger a retry.
 *
 * <p>Example usage:
 * <pre>{@code
 * RetryPolicy policy = new RetryPolicy(1_000, 30_000, Integer.MAX_VALUE);
 * policy.execute(() -> socket = new Socket(host, port));
 * }</pre>
 */
public final class RetryPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(RetryPolicy.class);

    private final long initialDelayMs;
    private final long maxDelayMs;
    private final int maxAttempts;

    /**
     * Creates a retry policy.
     *
     * @param initialDelayMs delay before the first retry (ms)
     * @param maxDelayMs     maximum delay between retries (ms)
     * @param maxAttempts    maximum number of attempts; use {@link Integer#MAX_VALUE} for unlimited
     */
    public RetryPolicy(long initialDelayMs, long maxDelayMs, int maxAttempts) {
        if (initialDelayMs <= 0) {
            throw new IllegalArgumentException("initialDelayMs must be > 0");
        }
        if (maxDelayMs < initialDelayMs) {
            throw new IllegalArgumentException("maxDelayMs must be >= initialDelayMs");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Executes the given task, retrying on {@link ConnectException} with
     * exponential backoff.
     *
     * @param task the operation to attempt
     * @throws ConnectException if {@code maxAttempts} is exhausted
     * @throws IOException      for any non-connect I/O error (propagated immediately)
     * @throws InterruptedException if the thread is interrupted during backoff sleep
     */
    public void execute(ConnectTask task) throws IOException, InterruptedException {
        long delayMs = initialDelayMs;
        int attempt = 0;

        while (true) {
            attempt++;
            try {
                task.run();
                return; // success
            } catch (ConnectException e) {
                if (attempt >= maxAttempts) {
                    LOG.error("Connection failed after {} attempt(s). Giving up.", attempt);
                    throw e;
                }
                LOG.warn("Connection attempt {}/{} failed: {}. Retrying in {} ms...",
                        attempt, maxAttempts == Integer.MAX_VALUE ? "∞" : maxAttempts,
                        e.getMessage(), delayMs);
                Thread.sleep(delayMs);
                delayMs = Math.min(delayMs * 2, maxDelayMs);
            }
            // Any other IOException propagates immediately — no retry
        }
    }

    /**
     * A task that may throw {@link IOException}, suitable for use with {@link RetryPolicy}.
     */
    @FunctionalInterface
    public interface ConnectTask {
        void run() throws IOException;
    }
}
