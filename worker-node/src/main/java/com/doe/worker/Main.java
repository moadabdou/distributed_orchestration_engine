package com.doe.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder main class for the worker-node module.
 */
public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        LOG.info("Module: worker-node");
        System.out.println("worker-node module loaded.");
    }
}
