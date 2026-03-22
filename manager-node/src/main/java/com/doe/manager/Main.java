package com.doe.manager;

import com.doe.manager.server.ManagerServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Entry point for the Manager Node.
 * <p>
 * Starts the {@link ManagerServer} on a configurable TCP port.
 * <p>
 * Usage: {@code java -jar manager-node.jar [--port <N>]}
 */
public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final int DEFAULT_PORT = 9090;

    public static void main(String[] args) {
        int port = parsePort(args);

        ManagerServer server = new ManagerServer(port);

        // Register JVM shutdown hook for graceful SIGINT handling
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual()
                .unstarted(server::shutdown));

        LOG.info("Starting Manager Node on port {}...", port);

        try {
            server.start();
        } catch (IOException e) {
            LOG.error("Failed to start ManagerServer", e);
            System.exit(1);
        }
    }

    /**
     * Parses the {@code --port <N>} argument from the command line.
     *
     * @param args CLI arguments
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
