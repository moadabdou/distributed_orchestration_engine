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
        String host = parseArg(args, "--host", DEFAULT_HOST);
        int port = parsePort(args);
        String authToken = parseArg(args, "--auth-token", System.getenv("WORKER_AUTH_TOKEN"));

        if (authToken == null || authToken.isBlank()) {
            if (hasArg(args, "--dev")) {
                String devValue = parseArg(args, "--dev", null);
                UUID workerId;
                
                if (devValue != null && !devValue.startsWith("--")) {
                    workerId = UUID.nameUUIDFromBytes(devValue.getBytes(StandardCharsets.UTF_8));
                } else {
                    workerId = UUID.randomUUID();
                }

                String secret = "3c34e62a26514757c2c159851f50a80d46dddc7fa0a06df5c689f928e4e9b94z";
                authToken = Jwts.builder()
                        .subject(workerId.toString())
                        .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                        .compact();
                
                LOG.info("DEV MODE ENABLED: Auto-generated JWT testing token for Worker ID: {}", workerId);
            } else {
                LOG.error("No auth token provided. Specify --auth-token, set WORKER_AUTH_TOKEN, or pass --dev [id] for testing.");
                System.exit(1);
            }
        }

        WorkerClient client = new WorkerClient(host, port, 5000, 10000, authToken);

        // Graceful shutdown on SIGINT / SIGTERM
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual()
                .unstarted(client::shutdown));

        LOG.info("Starting Worker Node, connecting to {}:{}...", host, port);
        client.start();
    }

    // ──── Helpers ────────────────────────────────────────────────────────────

    /**
     * Parses a named string argument (e.g. {@code --host localhost}).
     *
     * @param args         the CLI argument array
     * @param flag         the flag name, e.g. {@code "--host"}
     * @param defaultValue the value to return when the flag is absent
     * @return the parsed value, or {@code defaultValue}
     */
    static String parseArg(String[] args, String flag, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
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
     *
     * @param args CLI argument array
     * @return the port number, or {@link #DEFAULT_PORT} if not specified
     */
    static int parsePort(String[] args) {
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
        return DEFAULT_PORT;
    }
}
