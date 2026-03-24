# Milestone 5 — Scaling & Concurrency

**Goal**: Transform the system from single-job sequential processing into a high-throughput, concurrent orchestrator capable of assigning multiple jobs per worker based on capacity.

## Context
Up until M4, workers execute only one job at a time synchronously. If a worker gets a heavy job, it blocks, even if it has CPU capacity to do more. This milestone updates both the client and manager to support asynchronous execution, dynamic capacity-based scheduling, and forceful cancellation of errant jobs.

## Tasks
1. [**Issue #021 — Asynchronous Worker Execution**](issue-021-async-worker-client.md)
   Unblock the worker read loop by delegating execution to a `ThreadPoolExecutor`.
2. [**Issue #022 — Concurrent Job Assignments per Worker**](issue-022-concurrent-job-assignments.md)
   Update the manager domain models (`WorkerConnection`) to track `activeJobCount` and `maxCapacity` instead of a simple boolean busy state.
3. [**Issue #023 — Manager-Initiated Cancel Job Command**](issue-023-cancel-job-command.md)
   Introduce a new network command `CANCEL_JOB`. The worker intercepts it, identifies the underlying thread/Future, and aggressively interrupts it to save resources.
4. [**Issue #024 — Resource-Aware Scheduler**](issue-024-resource-aware-scheduler.md)
   Refactor `JobScheduler` to evaluate available capacity (`maxCapacity - activeJobCount`) across all workers before assigning jobs, rather than strictly queuing until a worker is fully idle.

## Acceptance Criteria
- [ ] A single worker can simultaneously execute up to `N` jobs (configurable).
- [ ] Manager can explicitly command a worker to drop an actively running job, which halts the JVM thread executing it and gracefully returns standard COMPLETED/FAILED.
- [ ] `JobScheduler` appropriately routes bursts of newly submitted jobs to workers with capacity rather than stalling.
