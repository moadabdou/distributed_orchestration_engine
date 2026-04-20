package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.JobDefinition;
import com.doe.core.executor.TaskExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plugin for {@code "type": "bash"} jobs. Executes a shell script using
 * {@code bash -c} via {@link ProcessBuilder}.
 */
public class ShellScriptPlugin implements TaskExecutor {

    private static final Gson GSON = new Gson();
    private static final long DEFAULT_TIMEOUT_MS = 300_000; // 5 minutes

    private final AtomicReference<Process> activeProcess = new AtomicReference<>();

    @Override
    public String getType() {
        return "bash";
    }

    @Override
    public String execute(JobDefinition definition, ExecutionContext context) throws Exception {
        JsonObject json = GSON.fromJson(definition.payload(), JsonObject.class);
        if (json == null || !json.has("script")) {
            throw new IllegalArgumentException("bash payload requires a 'script' field");
        }

        String script = json.get("script").getAsString();
        // Automatically prepend set -e and set -o pipefail to ensure script fails fast on any error
        if (!script.trim().startsWith("#!")) {
            script = "set -e\nset -o pipefail\n" + script;
        } else {
            // If it has a shebang, we should be careful, but for bash -c we can still inject it after the first line
            String[] lines = script.split("\n", 2);
            if (lines.length > 1) {
                script = lines[0] + "\nset -e\nset -o pipefail\n" + lines[1];
            }
        }

        long timeoutMs = json.has("timeoutMs")
                ? json.get("timeoutMs").getAsLong()
                : DEFAULT_TIMEOUT_MS;

        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("bash 'timeoutMs' must be positive, got: " + timeoutMs);
        }

        ProcessBuilder pb = new ProcessBuilder("bash", "-c", script);
        pb.redirectErrorStream(true); // merge stderr into stdout
        
        // Add environment variables from context
        pb.environment().putAll(context.getEnvVars());
        pb.environment().putAll(context.getSecrets());

        // Create a temporary workspace for the job
        java.nio.file.Path workspace = java.nio.file.Files.createTempDirectory("job-" + definition.jobId() + "-");
        pb.directory(workspace.toFile());

        context.log("Starting bash script in workspace: " + workspace);
        context.log("Script preview: " + (script.length() > 50 ? script.substring(0, 47) + "..." : script));
        
        long startTime = System.currentTimeMillis();
        Process process = pb.start();
        activeProcess.set(process);

        SubprocessBridge bridge = new SubprocessBridge(process, context, context.getXComClient(), definition.jobId());
        bridge.start();

        try {
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "bash script exceeded timeout of " + timeoutMs + "ms and was killed");
            }

            int exitCode = process.exitValue();
            long durationMs = System.currentTimeMillis() - startTime;

            if (exitCode != 0) {
                throw new IllegalStateException("Process exited with code " + exitCode);
            }

            return String.format("Shell script executed successfully in %d ms", durationMs);
        } finally {
            process.destroy();
            activeProcess.set(null);
            // Cleanup workspace
            try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(workspace)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(java.io.File::delete);
            } catch (java.io.IOException e) {
                context.log("WARN: Failed to cleanup workspace " + workspace + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void cancel() {
        Process p = activeProcess.get();
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
        }
    }

    @Override
    public void validate(JobDefinition definition) {
        JsonObject json = GSON.fromJson(definition.payload(), JsonObject.class);
        if (json == null || !json.has("script")) {
            throw new IllegalArgumentException("bash payload requires a 'script' field");
        }
    }
}
