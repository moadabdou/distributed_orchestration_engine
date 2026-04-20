package com.doe.worker.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton-like registry to correlate XCom requests sent over the shared TCP socket
 * with asynchronous responses from the Manager.
 */
public class XComCorrelationRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(XComCorrelationRegistry.class);

    private static final XComCorrelationRegistry INSTANCE = new XComCorrelationRegistry();

    public static XComCorrelationRegistry getInstance() {
        return INSTANCE;
    }

    private final Map<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();

    private XComCorrelationRegistry() {}

    public void register(String correlationId, CompletableFuture<String> future) {
        pendingRequests.put(correlationId, future);
    }

    public void complete(String correlationId, String result) {
        CompletableFuture<String> future = pendingRequests.remove(correlationId);
        if (future != null) {
            future.complete(result);
        } else {
            LOG.warn("Received response for unknown or timed-out correlationId: {}", correlationId);
        }
    }

    public void fail(String correlationId, Throwable cause) {
        CompletableFuture<String> future = pendingRequests.remove(correlationId);
        if (future != null) {
            future.completeExceptionally(cause);
        }
    }

    public void remove(String correlationId) {
        pendingRequests.remove(correlationId);
    }
}
