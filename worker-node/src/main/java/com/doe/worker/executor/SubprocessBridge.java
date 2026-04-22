package com.doe.worker.executor;

import com.doe.core.executor.ExecutionContext;
import com.doe.core.executor.XComClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Bridges a Subprocess with the Worker node and Manager.
 * Handles Stderr draining and Stdout proxying with XCom detection.
 */
public class SubprocessBridge {

    private static final Logger LOG = LoggerFactory.getLogger(SubprocessBridge.class);
    private static final Gson GSON = new Gson();
    private static final String XCOM_CMD_PREFIX = "__FERN_CMD__Xcom:";
    private static final String LOG_CMD_PREFIX = "__FERN_CMD__LOG:";

    private final Process process;
    private final ExecutionContext context;
    private final XComClient xComClient;
    private final UUID jobId;

    public SubprocessBridge(Process process, ExecutionContext context, XComClient xComClient, UUID jobId) {
        this.process = process;
        this.context = context;
        this.xComClient = xComClient;
        this.jobId = jobId;
    }

    public void start() {
        Thread.ofVirtual().name("stderr-drainer-" + jobId).start(this::drainStderr);
        Thread.ofVirtual().name("stdout-proxy-" + jobId).start(this::proxyStdout);
    }

    private void drainStderr() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                context.log("[STDERR] " + line);
            }
        } catch (IOException e) {
            if (!isProcessClosed()) {
                LOG.error("Error draining stderr for job {}", jobId, e);
            }
        }
    }

    private void proxyStdout() {
        LOG.info("SubprocessBridge: Starting stdout proxy for job {}", jobId);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOG.info("SubprocessBridge [stdout] for job {}: {}", jobId, line);
                if (line.startsWith(XCOM_CMD_PREFIX)) {
                    LOG.info("SubprocessBridge: Detected XCom command for job {}", jobId);
                    handleXComCommand(line.substring(XCOM_CMD_PREFIX.length()));
                } else if (line.startsWith(LOG_CMD_PREFIX)) {
                    context.log(line.substring(LOG_CMD_PREFIX.length()));
                } else {
                    LOG.debug("SubprocessBridge: Ignoring non-prefixed stdout line for job {}: {}", jobId, line);
                }
            }
        } catch (IOException e) {
            if (!isProcessClosed()) {
                LOG.error("Error proxying stdout for job {}", jobId, e);
            }
        }
        LOG.info("SubprocessBridge: Stdout proxy finished for job {}", jobId);
    }

    private void handleXComCommand(String payloadJson) {
        try {
            JsonObject json = GSON.fromJson(payloadJson, JsonObject.class);
            String command = json.has("command") ? json.get("command").getAsString() : "unknown";
            String key = json.has("key") ? json.get("key").getAsString() : "unknown";

            LOG.info("SubprocessBridge: Handling XCom command '{}' for key '{}', job {}", command, key, jobId);

            if ("push".equalsIgnoreCase(command)) {
                JsonElement valElement = json.get("value");
                String value = valElement != null ? (valElement.isJsonPrimitive() ? valElement.getAsString() : GSON.toJson(valElement)) : "";
                String type = json.has("type") ? json.get("type").getAsString() : "message";
                xComClient.push(key, value, type);
                LOG.info("SubprocessBridge: XCom push completed, sending ACK to job {}", jobId);
                writeToStdin("ACK\n");
            } else if ("pull".equalsIgnoreCase(command)) {
                String value = xComClient.pull(key);
                JsonObject response = new JsonObject();
                response.addProperty("status", value != null ? "SUCCESS" : "NOT_FOUND");
                response.addProperty("key", key);
                response.addProperty("value", value);
                String responseJson = GSON.toJson(response);
                LOG.info("SubprocessBridge: XCom pull completed, sending response to job {}", jobId);
                writeToStdin(responseJson + "\n");
            } else {
                LOG.warn("SubprocessBridge: Unknown XCom command '{}' for job {}", command, jobId);
                context.log("[ERROR] Unknown XCom command: " + command);
            }
        } catch (Exception e) {
            LOG.error("SubprocessBridge: Failed to handle XCom command for job {}", jobId, e);
        }
    }

    private synchronized void writeToStdin(String message) {
        try {
            OutputStream os = process.getOutputStream();
            os.write(message.getBytes(StandardCharsets.UTF_8));
            os.flush(); // Crucial to ensure the subprocess receives it
            LOG.debug("SubprocessBridge: Wrote {} bytes to stdin of job {}", message.length(), jobId);
        } catch (IOException e) {
            LOG.error("SubprocessBridge: Failed to write to subprocess stdin for job {}", jobId, e);
        }
    }

    private boolean isProcessClosed() {
        return !process.isAlive();
    }
}
