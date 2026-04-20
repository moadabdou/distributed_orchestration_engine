package com.doe.worker;

import com.doe.worker.client.WorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Entry point for the Worker Node.
 * <p>
 * Starts the {@link WorkerClient} targeting a configurable Manager host/port.
 * <p>
 * Usage: {@code java -jar worker-node.jar [--host <H>] [--port <N>]}
 */
public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 9090;

    public static void main(String[] args) {
        String host = parseArg(args, "--host", System.getenv("MANAGER_HOST"), DEFAULT_HOST);
        int port = parsePort(args, System.getenv("MANAGER_PORT"));
        String authToken = parseArg(args, "--auth-token", System.getenv("WORKER_AUTH_TOKEN"), null);
        boolean devMode = hasArg(args, "--dev") || "true".equalsIgnoreCase(System.getenv("WORKER_DEV_MODE"));
        
        // MinIO Storage Config
        String minioEndpoint = parseArg(args, "--minio-endpoint", System.getenv("MINIO_ENDPOINT"), "http://localhost:9000");
        parseArg(args, "--minio-access-key", System.getenv("MINIO_ACCESS_KEY"), "admin");
        parseArg(args, "--minio-secret-key", System.getenv("MINIO_SECRET_KEY"), "password123");
        String minioBucket = parseArg(args, "--minio-bucket", System.getenv("MINIO_BUCKET"), "fernos-storage");

        if (authToken == null || authToken.isBlank()) {
            if (devMode) {
                String devValue = parseArg(args, "--dev", null, null);
                UUID workerId;

                if (devValue != null && !devValue.startsWith("--")) {
                    workerId = UUID.nameUUIDFromBytes(devValue.getBytes(StandardCharsets.UTF_8));
                } else {
                    workerId = UUID.randomUUID();
                }

                String secret = System.getenv("MANAGER_SECURITY_JWT_SECRET");
                if (secret == null || secret.isBlank()) {
                    LOG.error("MANAGER_SECURITY_JWT_SECRET environment variable is required for dev mode.");
                    System.exit(1);
                }
                authToken = Jwts.builder()
                        .subject(workerId.toString())
                        .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                        .compact();

                LOG.info("DEV MODE ENABLED: Auto-generated JWT testing token for Worker ID: {}", workerId);
            } else {
                LOG.error("No auth token provided. Specify --auth-token, set WORKER_AUTH_TOKEN, or pass --dev for testing.");
                System.exit(1);
            }
        }

        int readTimeoutMs = parseTimeout(args, System.getenv("WORKER_READ_TIMEOUT_MS"));
        int heartbeatIntervalMs = parseHeartbeatInterval(args, System.getenv("WORKER_HEARTBEAT_INTERVAL_MS"));

        WorkerClient client = new WorkerClient(host, port, heartbeatIntervalMs, readTimeoutMs, authToken);

        // Graceful shutdown on SIGINT / SIGTERM
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual()
                .unstarted(client::shutdown));

        LOG.info("Starting Worker Node, connecting to {}:{}...", host, port);
        LOG.info("MinIO Configured: endpoint={}, bucket={}", minioEndpoint, minioBucket);
        client.start();
    }

    // ──── Helpers ────────────────────────────────────────────────────────────

    /**
     * Parses a named string argument (e.g. {@code --host localhost}).
     * Falls back to environment variable, then to the provided default.
     *
     * @param args         the CLI argument array
     * @param flag         the flag name, e.g. {@code "--host"}
     * @param envFallback  environment variable value to try if flag is absent
     * @param defaultValue the value to return when both flag and env var are absent
     * @return the parsed value, or {@code defaultValue}
     */
    static String parseArg(String[] args, String flag, String envFallback, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        if (envFallback != null && !envFallback.isBlank()) {
            return envFallback;
        }
        return defaultValue;
    }

    /**
     * Checks if the argument array contains a specific flag.
     *
     * @param args the CLI argument array
     * @param flag the flag name, e.g. {@code "--dev"}
     * @return true if the flag is present
     */
    static boolean hasArg(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses the {@code --port <N>} argument from CLI args.
     * Falls back to environment variable, then to the provided default.
     *
     * @param args        CLI argument array
     * @param envFallback environment variable value to try if flag is absent (may be null)
     * @return the port number, or {@link #DEFAULT_PORT} if not specified
     */
    static int parsePort(String[] args, String envFallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    LOG.error("Invalid port number: {}", args[i + 1]);
                    System.exit(1);
                }
            }
        }
        if (envFallback != null && !envFallback.isBlank()) {
            try {
                return Integer.parseInt(envFallback);
            } catch (NumberFormatException e) {
            }
        }
        return DEFAULT_PORT;
    }


    /**


     * Parses the {@code --read-timeout <N>} argument from CLI args.
     * Falls back to environment variable, then to 20 minutes.
     *
     * @param args        CLI argument array
     * @param envFallback environment variable value to try if flag is absent (may be null)
     * @return the timeout in milliseconds
     */
    static int parseTimeout(String[] args, String envFallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--read-timeout".equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    LOG.error("Invalid timeout value: {}", args[i + 1]);
                    System.exit(1);
                }
            }
        }
        if (envFallback != null && !envFallback.isBlank()) {
            try {
                return Integer.parseInt(envFallback);
            } catch (NumberFormatException e) {
                LOG.error("Invalid WORKER_READ_TIMEOUT_MS env value: {}", envFallback);
            }
        }
        return WorkerClient.DEFAULT_READ_TIMEOUT_MS;
    }

    /**
     * Parses the {@code --heartbeat-interval <N>} argument from CLI args.
     * Falls back to environment variable, then to 5000 milliseconds.
     *
     * @param args        CLI argument array
     * @param envFallback environment variable value to try if flag is absent (may be null)
     * @return the heartbeat interval in milliseconds
     */
    static int parseHeartbeatInterval(String[] args, String envFallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--heartbeat-interval".equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    LOG.error("Invalid heartbeat interval value: {}", args[i + 1]);
                    System.exit(1);
                }
            }
        }
        if (envFallback != null && !envFallback.isBlank()) {
            try {
                return Integer.parseInt(envFallback);
            } catch (NumberFormatException e) {
                LOG.error("Invalid WORKER_HEARTBEAT_INTERVAL_MS env value: {}", envFallback);
            }
        }
        return (int) WorkerClient.DEFAULT_HEARTBEAT_INTERVAL_MS;
    }
}

