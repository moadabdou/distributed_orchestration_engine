#030 — Concurrent Job Assignments per Worker

**Milestone:** 5 — Scaling & Concurrency  
**Labels:** `manager-node`, `concurrency`, `priority:high`

## Description

The `ManagerServer` must fundamentally support the idea of a worker executing more than one job. Currently, `WorkerConnection` tracks a single `Job currentJob` and uses simplistic `trySetBusy` and `setIdle` flag controls. We must modernize this to a full capacity scale.

## Acceptance Criteria

- [ ] Update `WorkerConnection` to encapsulate an `int maxCapacity` configuration variable (defaulted to 4).
- [ ] Replace `setBusy`/`setIdle` logic with thread-safe `AtomicInteger activeJobCount`.
- [ ] Introduce methods `boolean tryReserveCapacity()` and `void releaseCapacity()`.
- [ ] Change the `currentJob` registry per worker to an inner `Set<UUID> activeJobs`.
- [ ] Ensure that during a worker heartbeat timeout (crash recovery), **all** jobs currently registered dynamically to that specific worker's active set are uniformly fetched and independently re-queued.
