package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.JobDefinition;
import com.doe.core.executor.XComClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Default implementation of {@link ExecutionContext} used by the worker.
 */
public class DefaultExecutionContext implements ExecutionContext {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultExecutionContext.class);
    private static final long DEFAULT_MAX_LOG_SIZE = 1_000_000; // 1MB in characters

    private final JobDefinition definition;
    private final XComClient xComClient;
    private final Map<String, String> envVars;
    
    private final List<String> logBuffer = new CopyOnWriteArrayList<>();
    private final AtomicLong currentLogSize = new AtomicLong(0);
    private final long maxLogSize;
    private volatile boolean logsTruncated = false;
    private volatile Consumer<String> logListener;

    private static Map<String, String> loadMinioEnvVars() {
        Map<String, String> vars = new HashMap<>();
        String[] minioKeys = {"MINIO_ENDPOINT", "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY", "MINIO_BUCKET"};
        for (String key : minioKeys) {
            String value = System.getenv(key);
            if (value != null) {
                vars.put(key, value);
            }
        }
        return Collections.unmodifiableMap(vars);
    }


    public DefaultExecutionContext(JobDefinition definition, Map<String, String> envVars, Map<String, String> secrets, XComClient xComClient) {
        this(definition, envVars, secrets, xComClient, DEFAULT_MAX_LOG_SIZE);
    }

    public DefaultExecutionContext(JobDefinition definition, Map<String, String> envVars, Map<String, String> secrets, XComClient xComClient, long maxLogSize) {
        this.definition = definition;
        // Merge provided envVars with defaults (like Minio)
        this.envVars = merge(loadMinioEnvVars(), envVars);
        // Note: secrets aren't currently handled in this simplified version, but we'll keep the param for future
        this.xComClient = xComClient;
        this.maxLogSize = maxLogSize;
    }

    private Map<String, String> merge(Map<String, String> base, Map<String, String> overrides) {
        Map<String, String> merged = new HashMap<>(base);
        merged.putAll(overrides);
        return Collections.unmodifiableMap(merged);
    }

    @Override
    public JobDefinition getDefinition() {
        return definition;
    }

    @Override
    public Map<String, String> getEnvVars() {
        // We merge them in constructor or load on demand. Returning Minio env for now.
        return envVars;
    }

    @Override
    public Map<String, String> getSecrets() {
        return Collections.emptyMap();
    }

    @Override
    public XComClient getXComClient() {
        return xComClient;
    }

    @Override
    public void log(String message) {
        if (logsTruncated) {
            return;
        }

        if (message == null) {
            message = "null";
        }

        long newSize = currentLogSize.addAndGet(message.length());
        if (newSize > maxLogSize) {
            logsTruncated = true;
            String truncationMsg = "[LOGS TRUNCATED] Maximum log size reached (" + maxLogSize + " characters)";
            logBuffer.add(truncationMsg);
            LOG.warn(truncationMsg);
            return;
        }

        logBuffer.add(message);
        LOG.info("[TASK-LOG] {}", message);
        
        Consumer<String> listener = logListener;
        if (listener != null) {
            listener.accept(message);
        }
    }

    @Override
    public List<String> getBufferedLogs() {
        return Collections.unmodifiableList(logBuffer);
    }

    @Override
    public long getBufferedLogsSize() {
        return currentLogSize.get();
    }

    public UUID getWorkflowId() {
        return definition.workflowId();
    }

    public UUID getJobId() {
        return definition.jobId();
    }

    @Override
    public void setLogListener(Consumer<String> listener) {
        this.logListener = listener;
    }
}
