package com.doe.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RetryPolicy}.
 */
class RetryPolicyTest {

    // ──── Constructor validation ────────────────────────────────────────────

    @Test
    @DisplayName("Constructor rejects initialDelayMs <= 0")
    void constructor_rejectsNonPositiveInitialDelay() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(0, 1_000, 3));
    }

    @Test
    @DisplayName("Constructor rejects maxDelayMs < initialDelayMs")
    void constructor_rejectsMaxDelayLessThanInitial() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(2_000, 1_000, 3));
    }

    @Test
    @DisplayName("Constructor rejects maxAttempts <= 0")
    void constructor_rejectsNonPositiveMaxAttempts() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(100, 1_000, 0));
    }

    // ──── Success on first attempt ──────────────────────────────────────────

    @Test
    @DisplayName("Succeeds immediately when task does not throw")
    void execute_succeedsOnFirstAttempt() throws IOException, InterruptedException {
        RetryPolicy policy = new RetryPolicy(100, 1_000, 3);
        AtomicInteger callCount = new AtomicInteger();

        policy.execute(callCount::incrementAndGet);

        assertEquals(1, callCount.get(), "Task should be called exactly once");
    }

    // ──── Retry on ConnectException ─────────────────────────────────────────

    @Test
    @DisplayName("Retries on ConnectException and succeeds on 3rd attempt")
    void execute_retriesOnConnectException() throws IOException, InterruptedException {
        RetryPolicy policy = new RetryPolicy(1, 50, Integer.MAX_VALUE); // tiny delays for speed
        AtomicInteger callCount = new AtomicInteger();

        policy.execute(() -> {
            if (callCount.incrementAndGet() < 3) {
                throw new ConnectException("Connection refused");
            }
        });

        assertEquals(3, callCount.get(), "Should have been called 3 times");
    }

    @Test
    @DisplayName("Propagates ConnectException after maxAttempts exhausted")
    void execute_propagatesAfterMaxAttempts() {
        RetryPolicy policy = new RetryPolicy(1, 50, 2);
        AtomicInteger callCount = new AtomicInteger();

        ConnectException thrown = assertThrows(ConnectException.class, () ->
                policy.execute(() -> {
                    callCount.incrementAndGet();
                    throw new ConnectException("Connection refused");
                }));

        assertEquals(2, callCount.get(), "Should have attempted exactly maxAttempts times");
        assertNotNull(thrown);
    }

    // ──── Non-connect IOException propagates immediately ────────────────────

    @Test
    @DisplayName("Non-ConnectException IOException propagates immediately without retry")
    void execute_propagatesNonConnectIoExceptionImmediately() {
        RetryPolicy policy = new RetryPolicy(1, 50, 5);
        AtomicInteger callCount = new AtomicInteger();

        assertThrows(IOException.class, () ->
                policy.execute(() -> {
                    callCount.incrementAndGet();
                    throw new IOException("Unexpected I/O error");
                }));

        assertEquals(1, callCount.get(), "Should not retry on non-ConnectException IOExceptions");
    }

    // ──── Backoff capping ───────────────────────────────────────────────────

    @Test
    @DisplayName("Backoff delay does not exceed maxDelayMs")
    void execute_backoffCappedAtMax() throws IOException, InterruptedException {
        // 4 failures; delay sequence would be 1ms, 2ms, 4ms (all < max of 10ms)
        // We just verify it doesn't hang or exceed wall time unreasonably.
        RetryPolicy policy = new RetryPolicy(1, 10, Integer.MAX_VALUE);
        AtomicInteger callCount = new AtomicInteger();

        long start = System.currentTimeMillis();
        policy.execute(() -> {
            if (callCount.incrementAndGet() < 5) {
                throw new ConnectException("refused");
            }
        });
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(5, callCount.get());
        // 4 sleeps, each at most 10 ms (plus a generous margin for JVM overhead)
        assertTrue(elapsed < 1_000,
                "Total elapsed %dms is too long; backoff cap may be broken".formatted(elapsed));
    }

    // ──── Single attempt succeeds immediately ───────────────────────────────

    @Test
    @DisplayName("maxAttempts=1 propagates ConnectException on first failure")
    void execute_singleAttemptFails() {
        RetryPolicy policy = new RetryPolicy(1, 50, 1);

        assertThrows(ConnectException.class, () ->
                policy.execute(() -> {
                    throw new ConnectException("refused");
                }));
    }
}
