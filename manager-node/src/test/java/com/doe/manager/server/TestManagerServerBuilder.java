package com.doe.manager.server;

import com.doe.core.event.EngineEventListener;
import com.doe.core.registry.WorkerRegistry;
import com.doe.core.registry.JobRegistry;
import com.doe.manager.scheduler.JobQueue;
import com.doe.manager.scheduler.JobScheduler;
import com.doe.manager.scheduler.CrashRecoveryHandler;
import com.doe.manager.scheduler.JobTimeoutMonitor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TestManagerServerBuilder {

    /** A no-op listener used in integration tests that don't need a real DB. */
    public static final EngineEventListener NO_OP_LISTENER = new EngineEventListener() {
        @Override public void onWorkerRegistered(UUID w, String h, String ip, Instant t) {}
        @Override public void onWorkerHeartbeat(UUID w, Instant t) {}
        @Override public void onWorkerDied(UUID w) {}
        @Override public void onWorkerBusy(UUID w) {}
        @Override public void onWorkerIdle(UUID w) {}
        @Override public void onJobAssigned(UUID j, UUID w, Instant t) {}
        @Override public void onJobRunning(UUID j, Instant t) {}
        @Override public void onJobCompleted(UUID j, String r, Instant t) {}
        @Override public void onJobFailed(UUID j, String r, Instant t) {}
        @Override public void onJobRequeued(UUID j, int retry, Instant t) {}
    };

    public static ManagerServer build(int port, long check, long timeout) {
        WorkerRegistry registry = new WorkerRegistry();
        JobRegistry jobRegistry = new JobRegistry();
        JobQueue jobQueue = new JobQueue(jobRegistry);
        JobScheduler jobScheduler = new JobScheduler(jobQueue, registry, NO_OP_LISTENER);
        CrashRecoveryHandler recoveryHandler = new CrashRecoveryHandler(jobRegistry, jobQueue, NO_OP_LISTENER);
        JobTimeoutMonitor jobTimeoutMonitor = new JobTimeoutMonitor(jobRegistry, recoveryHandler);
        return new ManagerServer(port, check, timeout, "3c34e62a26514757c2c159851f50a80d46dddc7fa0a06df5c689f928e4e9b94z", registry, jobRegistry, jobScheduler, jobTimeoutMonitor,
                NO_OP_LISTENER, List.of(recoveryHandler));
    }

    public static ManagerServer build(int port) {
        return build(port, 5000, 15000);
    }
}
