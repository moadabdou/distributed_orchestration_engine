package com.doe.worker.executor;

import com.doe.core.executor.TaskExecutor;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Plugin for {@code "type": "bash"} jobs. Executes a shell script using
 * {@code bash -c} via {@link ProcessBuilder} and captures combined stdout+stderr.
 *
 * <h2>Expected payload</h2>
 * <pre>
 * {
 *   "type":      "bash",
 *   "script":    "echo hello world",
 *   "timeoutMs": 5000
 * }
 * </pre>
 *
 * <h2>Timeout layering</h2>
 * The plugin honours the payload-level {@code timeoutMs} field by calling
 * {@link Process#waitFor(long, TimeUnit)} and force-killing the process on
 * breach. However, {@code WorkerClient} also wraps every plugin invocation in
 * a {@code CompletableFuture.orTimeout()} — that outer timeout is the
 * <em>hard wall</em>. The plugin-level timeout is a best-effort, OS-level
 * signal that fires first and guarantees the process is dead even if the
 * calling thread is interrupted before the outer timeout can act.
 *
 * <h2>Output cap</h2>
 * Stdout+stderr are merged and captured into a buffer capped at
 * {@value #MAX_OUTPUT_BYTES} bytes. Output beyond the cap is silently
 * discarded and a warning suffix is appended so callers know truncation
 * occurred.
 */
public class ShellScriptPlugin implements TaskExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(ShellScriptPlugin.class);
    private static final Gson GSON = new Gson();

    /** Maximum bytes captured from process output before truncation (64 KB). */
    public static final int MAX_OUTPUT_BYTES = 64 * 1024;

    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final String TRUNCATION_SUFFIX = "\n[...output truncated at 64 KB...]";

    @Override
    public String execute(String payload) throws Exception {
        JsonObject json = GSON.fromJson(payload, JsonObject.class);
        if (json == null || !json.has("script")) {
            throw new IllegalArgumentException("bash payload requires a 'script' field");
        }

        String script = json.get("script").getAsString();
        long timeoutMs = json.has("timeoutMs")
                ? json.get("timeoutMs").getAsLong()
                : DEFAULT_TIMEOUT_MS;

        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("bash 'timeoutMs' must be positive, got: " + timeoutMs);
        }

        ProcessBuilder pb = new ProcessBuilder("bash", "-c", script);
        pb.redirectErrorStream(true); // merge stderr into stdout

        Process process = pb.start();

        // Capture output on a virtual thread concurrently so that waitFor() below
        // is not blocked by the read loop when a script produces no output.
        var outputFuture = CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return captureOutput(process.getInputStream());
                    } catch (IOException e) {
                        return "";
                    }
                });

        try {
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                throw new IllegalStateException(
                        "bash script exceeded timeout of " + timeoutMs + "ms and was killed");
            }

            int exitCode = process.exitValue();
            // Join output future (process has exited, so the stream is closed)
            String output = outputFuture.join();

            if (exitCode != 0) {
                String firstLine = output.lines().findFirst().orElse("(no output)");
                throw new IllegalStateException(
                        "Process exited with code " + exitCode + ": " + firstLine);
            }

            return output;
        } finally {
            process.destroy();
        }
    }

    /**
     * Reads up to {@value #MAX_OUTPUT_BYTES} bytes from the stream, returning
     * the content as a UTF-8 string. Appends a truncation notice when the cap
     * is hit so callers are aware output is incomplete.
     */
    private static String captureOutput(InputStream in) throws IOException {
        byte[] buffer = new byte[MAX_OUTPUT_BYTES];
        int totalRead = 0;

        int read;
        while (totalRead < MAX_OUTPUT_BYTES
                && (read = in.read(buffer, totalRead, MAX_OUTPUT_BYTES - totalRead)) != -1) {
            totalRead += read;
        }

        // Check if there is more data beyond the cap
        boolean truncated = false;
        if (totalRead == MAX_OUTPUT_BYTES) {
            int extra = in.read();
            if (extra != -1) {
                truncated = true;
                // Drain the rest so the process can exit cleanly
                in.transferTo(java.io.OutputStream.nullOutputStream());
            }
        }

        String output = new String(buffer, 0, totalRead, StandardCharsets.UTF_8);
        return truncated ? output + TRUNCATION_SUFFIX : output;
    }
}
