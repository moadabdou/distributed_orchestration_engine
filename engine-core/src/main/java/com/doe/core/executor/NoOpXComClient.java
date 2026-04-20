package com.doe.core.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default no-op implementation of {@link XComClient}.
 */
public class NoOpXComClient implements XComClient {
    private static final Logger LOG = LoggerFactory.getLogger(NoOpXComClient.class);

    @Override
    public void push(String key, String value, String type) {
        LOG.debug("XCom push (no-op): {} = {} (type: {})", key, value, type);
    }

    @Override
    public String pull(String key) {
        LOG.debug("XCom pull (no-op): {}", key);
        return null;
    }
}
