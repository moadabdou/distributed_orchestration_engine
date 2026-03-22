package com.doe.worker;

import com.doe.worker.client.WorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        WorkerClient client = new WorkerClient(host, port);

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
