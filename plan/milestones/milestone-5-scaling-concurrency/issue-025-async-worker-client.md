#029 — Asynchronous Worker Execution

**Milestone:** 5 — Scaling & Concurrency  
**Labels:** `worker-node`, `concurrency`, `priority:high`

## Description

Currently, the `WorkerClient` blocks its main read loop (which deserializes the TCP stream) whenever it's assigned a job. This synchronous block prevents the worker from acting on any other managerial commands until the active payload concludes or eventually hits its internal execution timeout.

We need to uncouple network reading from actual payload execution.

## Acceptance Criteria

- [ ] Update `WorkerClient` to dispatch the task execution (`CompletableFuture.supplyAsync`) dynamically to a configurable `ThreadPoolExecutor` (e.g., fixed pool of 4 by default).
- [ ] Return the main read loop to the top `while(running)` state immediately after dispatching a task to the background pool.
- [ ] Safely capture `Future<?>` references mapped by `jobId` locally in a concurrent registry `ConcurrentHashMap<String, Future<?>>`.
- [ ] Ensure `JOB_RESULT` and `JOB_RUNNING` events are still dispatched to the network safely without threading race conditions over the `OutputStream`.
