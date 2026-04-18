package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.JobDefinition;
import com.doe.core.executor.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A thread-safe registry that discovers {@link TaskExecutor} implementations via SPI
 * and provides fresh instances for each job execution.
 */
public class TaskPluginRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(TaskPluginRegistry.class);

    // Maps type string to the SPI provider
    private final ConcurrentMap<String, ServiceLoader.Provider<TaskExecutor>> providers = new ConcurrentHashMap<>();

    public TaskPluginRegistry() {
        loadPlugins();
    }

    private void loadPlugins() {
        ServiceLoader<TaskExecutor> loader = ServiceLoader.load(TaskExecutor.class);
        loader.stream().forEach(provider -> {
            // We need an instance just to get the type, then we store the provider
            TaskExecutor instance = provider.get();
            String type = instance.getType();
            if (type != null && !type.isBlank()) {
                providers.put(type, provider);
                LOG.info("Discovered executor plugin: {} -> {}", type, instance.getClass().getSimpleName());
            }
        });
    }

    /**
     * Returns a fresh instance of the executor for the given type.
     *
     * @param type the executor type
     * @return an Optional containing a new TaskExecutor instance, or empty if not found
     */
    public Optional<TaskExecutor> getExecutor(String type) {
        return Optional.ofNullable(providers.get(type)).map(ServiceLoader.Provider::get);
    }

    /**
     * Executes the job defined by {@code definition} using a fresh plugin instance.
     * 
     * <p>Note: Regular callers in the worker should probably use {@link #getExecutor(String)}
     * and call execute themselves if they need to track the instance for cancellation.
     *
     * @param definition task metadata and payload
     * @param context    execution environment and utilities
     * @return the result string produced by the plugin
     * @throws UnknownTaskTypeException  if no plugin is registered for the type
     * @throws Exception                 if the plugin itself throws
     */
    public String execute(JobDefinition definition, ExecutionContext context) throws Exception {
        TaskExecutor plugin = getExecutor(definition.type())
                .orElseThrow(() -> new UnknownTaskTypeException(definition.type()));

        return plugin.execute(definition, context);
    }

    /**
     * Registers a plugin instance manually (primarily for testing).
     * 
     * @param type   the executor type
     * @param plugin the executor instance
     * @return this registry
     */
    public TaskPluginRegistry register(String type, TaskExecutor plugin) {
        providers.put(type, new StaticProvider(plugin));
        return this;
    }

    private static record StaticProvider(TaskExecutor instance) implements ServiceLoader.Provider<TaskExecutor> {
        @Override public Class<? extends TaskExecutor> type() { return instance.getClass(); }
        @Override public TaskExecutor get() { return instance; }
    }
}
