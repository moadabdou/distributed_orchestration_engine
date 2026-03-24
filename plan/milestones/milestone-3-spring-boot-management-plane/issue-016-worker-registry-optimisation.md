# Issue #016 — Worker Registry Optimisation

**Milestone:** 3 — Spring Boot Management Plane  
**Labels:** `manager-node`, `performance`, `priority:medium`  
**Assignee:** —  
**Estimate:** 0.5 day  
**Depends on:** #011  

## Description

The `WorkerRegistry` currently iterates through all connected workers via `O(N)` traversal to find an `IDLE` worker. As the system scales, this iteration during every scheduler tick becomes a bottleneck. Convert the idle worker lookup to `O(1)` by maintaining a dedicated queue or set of idle workers alongside the main Map.

## Acceptance Criteria

- [ ] `WorkerRegistry` maintains a fast-access collection (e.g. `ConcurrentLinkedQueue`) of idle workers.
- [ ] `findIdle()` operates in `O(1)` time by popping an available worker.
- [ ] Worker state transitions (IDLE <-> BUSY) correctly add/remove from this fast-access collection.
- [ ] Existing `WorkerRegistry` tests pass.
