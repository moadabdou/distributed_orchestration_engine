package com.doe.core.registry;

import java.util.UUID;

/**
 * Observer interface for reacting to worker detach/death events.
 */
public interface WorkerDeathListener {

    /**
     * Called when a worker is detected to be dead or disconnected.
     *
     * @param workerId the ID of the dead worker
     */
    void onWorkerDeath(UUID workerId, java.util.Set<UUID> activeJobs);
}
