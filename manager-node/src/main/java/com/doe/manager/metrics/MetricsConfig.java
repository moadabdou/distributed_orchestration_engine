package com.doe.manager.metrics;

import com.doe.core.event.EngineEventListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Configuration that wires a composite {@link EngineEventListener} delegating
 * to all individual listener beans ({@code DatabaseEventListener}, {@code MetricsService}).
 */
@Configuration
public class MetricsConfig {

    /**
     * Creates a composite listener that forwards every event to all registered delegates.
     * <p>
     * Marked {@code @Primary} so Spring injects this bean wherever a single
     * {@code EngineEventListener} is expected (ManagerServer, JobScheduler, CrashRecoveryHandler).
     * <p>
     * Uses {@link ObjectProvider} to avoid circular dependency — delegates are resolved
     * lazily, excluding this composite bean itself.
     */
    @Primary
    @Bean
    public EngineEventListener compositeEngineEventListener(ObjectProvider<EngineEventListener> delegatesProvider) {
        // Resolve all EngineEventListener beans except this composite (Spring handles exclusion)
        List<EngineEventListener> delegates = delegatesProvider.orderedStream()
                .filter(d -> !(d instanceof CompositeDelegate))
                .collect(Collectors.toList());
        return new CompositeDelegate(delegates);
    }

    /**
     * Internal composite — delegates each event to all registered listeners.
     */
    static class CompositeDelegate implements EngineEventListener {

        private final List<EngineEventListener> delegates;

        CompositeDelegate(List<EngineEventListener> delegates) {
            this.delegates = delegates;
        }

        @Override
        public void onWorkerRegistered(UUID workerId, String hostname, String ipAddress, int maxCapacity, Instant registeredAt) {
            for (EngineEventListener d : delegates) d.onWorkerRegistered(workerId, hostname, ipAddress, maxCapacity, registeredAt);
        }

        @Override
        public void onWorkerHeartbeat(UUID workerId, Instant timestamp) {
            for (EngineEventListener d : delegates) d.onWorkerHeartbeat(workerId, timestamp);
        }

        @Override
        public void onWorkerDied(UUID workerId) {
            for (EngineEventListener d : delegates) d.onWorkerDied(workerId);
        }

        @Override
        public void onJobAssigned(UUID jobId, UUID workerId, Instant updatedAt) {
            for (EngineEventListener d : delegates) d.onJobAssigned(jobId, workerId, updatedAt);
        }

        @Override
        public void onJobRunning(UUID jobId, Instant updatedAt) {
            for (EngineEventListener d : delegates) d.onJobRunning(jobId, updatedAt);
        }

        @Override
        public void onJobCompleted(UUID jobId, UUID workerId, String result, Instant updatedAt) {
            for (EngineEventListener d : delegates) d.onJobCompleted(jobId, workerId, result, updatedAt);
        }

        @Override
        public void onJobFailed(UUID jobId, UUID workerId, String result, Instant updatedAt) {
            for (EngineEventListener d : delegates) d.onJobFailed(jobId, workerId, result, updatedAt);
        }

        @Override
        public void onJobCancelled(UUID jobId, UUID workerId, String result, Instant updatedAt) {
            for (EngineEventListener d : delegates) d.onJobCancelled(jobId, workerId, result, updatedAt);
        }

        @Override
        public void onJobRequeued(UUID jobId, int retryCount, Instant updatedAt) {
            for (EngineEventListener d : delegates) d.onJobRequeued(jobId, retryCount, updatedAt);
        }

        @Override
        public void onJobSkipped(UUID jobId, Instant updatedAt) {
            for (EngineEventListener d : delegates) d.onJobSkipped(jobId, updatedAt);
        }
    }
}
