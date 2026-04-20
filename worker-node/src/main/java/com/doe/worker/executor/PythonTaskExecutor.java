package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.JobDefinition;
import com.doe.core.executor.TaskExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plugin for {@code "type": "python"} jobs. Executes Python scripts, files, or notebooks.
 */
public class PythonTaskExecutor implements TaskExecutor {

    private static final Gson GSON = new Gson();
    private static final long DEFAULT_TIMEOUT_MS = 600_000; // 10 minutes

    private final AtomicReference<Process> activeProcess = new AtomicReference<>();

    @Override
    public String getType() {
        return "python";
    }

    @Override
    public String execute(JobDefinition definition, ExecutionContext context) throws Exception {
        JsonObject json = GSON.fromJson(definition.payload(), JsonObject.class);
        validatePayload(json);

        List<String> command = buildCommand(json);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        // Add environment variables from context
        pb.environment().putAll(context.getEnvVars());
        pb.environment().putAll(context.getSecrets());

        // Add custom env vars from payload
        if (json.has("env")) {
            JsonObject envJson = json.getAsJsonObject("env");
            for (Map.Entry<String, JsonElement> entry : envJson.entrySet()) {
                pb.environment().put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        long timeoutMs = json.has("timeoutMs") ? json.get("timeoutMs").getAsLong() : DEFAULT_TIMEOUT_MS;

        // Create a temporary workspace for the job
        java.nio.file.Path workspace = java.nio.file.Files.createTempDirectory("job-py-" + definition.jobId() + "-");
        pb.directory(workspace.toFile());

        context.log("Executing Python in workspace: " + workspace);
        context.log("Command: " + String.join(" ", command));
        
        long startTime = System.currentTimeMillis();
        Process process = pb.start();
        activeProcess.set(process);

        SubprocessBridge bridge = new SubprocessBridge(process, context, context.getXComClient(), definition.jobId());
        bridge.start();

        try {
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Python script exceeded timeout of " + timeoutMs + "ms");
            }

            int exitCode = process.exitValue();
            long durationMs = System.currentTimeMillis() - startTime;
            if (exitCode != 0) {
                throw new IllegalStateException("Python script exited with code " + exitCode);
            }

            return String.format("Executed successfully in %d ms", durationMs);
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

    private List<String> buildCommand(JsonObject json) throws IOException {
        List<String> command = new ArrayList<>();

        // 1. Determine interpreter
        String interpreter = "python3";
        if (json.has("venv")) {
            Path venvPath = Path.of(json.get("venv").getAsString());
            interpreter = venvPath.resolve("bin").resolve("python").toString();
        } else if (json.has("conda_env")) {
            command.add("conda");
            command.add("run");
            command.add("-n");
            command.add(json.get("conda_env").getAsString());
            command.add("python");
        } else if (json.has("interpreter")) {
            interpreter = json.get("interpreter").getAsString();
        }

        if (command.isEmpty()) {
            command.add(interpreter);
        } else {
            // conda run -n env python ...
        }

        // 2. Determine script/file/notebook
        if (json.has("notebook")) {
            // Re-evaluate command for papermill if needed, or just use papermill directly
            // For now, let's assume we use papermill command
            command.clear();
            command.add("papermill");
            command.add(json.get("notebook").getAsString());
            command.add("-"); // output to stdout
        } else if (json.has("script")) {
            // Use -c for small scripts or write to temp file? -c is safer for small snippets
            command.add("-c");
            command.add(json.get("script").getAsString());
        } else if (json.has("file")) {
            command.add(json.get("file").getAsString());
        }

        // 3. Add arguments
        if (json.has("args")) {
            JsonArray args = json.getAsJsonArray("args");
            for (JsonElement arg : args) {
                command.add(arg.getAsString());
            }
        }

        return command;
    }

    private void validatePayload(JsonObject json) {
        if (json == null) {
            throw new IllegalArgumentException("Python payload cannot be empty");
        }
        if (!json.has("script") && !json.has("file") && !json.has("notebook")) {
            throw new IllegalArgumentException("Python payload must contain 'script', 'file', or 'notebook'");
        }
    }

    @Override
    public void cancel() throws Exception {
        Process p = activeProcess.get();
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
        }
    }

    @Override
    public void validate(JobDefinition definition) throws Exception {
        JsonObject json = GSON.fromJson(definition.payload(), JsonObject.class);
        validatePayload(json);
    }
}
