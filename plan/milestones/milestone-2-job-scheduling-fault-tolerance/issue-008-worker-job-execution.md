# Issue #008 — Worker Job Execution & Result Reporting

**Milestone:** 2 — Job Scheduling & Fault Tolerance  
**Labels:** `worker-node`, `engine-core`, `execution`, `priority:high`  
**Assignee:** —  
**Estimate:** 1 day  
**Depends on:** #004, #006, #007  

## Description

Extend the `WorkerClient` to receive `ASSIGN_JOB` messages, execute a dummy computational task, and send back a `JOB_RESULT`.

### Execution Flow

```
1. Worker receives ASSIGN_JOB { jobId, payload }
2. Worker sends JOB_RUNNING { jobId } → Manager transitions job ASSIGNED → RUNNING
3. Log: "Executing job <jobId>"
4. Execute payload (initially: dummy tasks — sleep, compute Fibonacci, etc.)
5. On success → send JOB_RESULT { jobId, status: "COMPLETED", output: "..." }
6. On exception → catches, send JOB_RESULT { jobId, status: "FAILED", output: "<error>" }
7. Worker marks itself IDLE again (ready for next job)
```

## Acceptance Criteria

- [ ] Worker handles `ASSIGN_JOB` in its message loop
- [ ] Immediately upon receiving `ASSIGN_JOB`, worker sends `JOB_RUNNING { jobId }` before executing
- [ ] Manager handles `JOB_RUNNING`: transitions job `ASSIGNED → RUNNING` in `JobRegistry` / job map
- [ ] `TaskExecutor` interface: `String execute(String payload)` — pluggable execution strategy
- [ ] `DummyTaskExecutor`: interprets payload JSON, runs: `sleep(N)`, `fibonacci(N)`, or `echo`
- [ ] On success → sends `JOB_RESULT` with `COMPLETED` + output string
- [ ] On exception → catches, sends `JOB_RESULT` with `FAILED` + exception message
- [ ] Worker stays alive after job completion and accepts more jobs

## Technical Notes

- Execute tasks on the connection thread (Virtual Thread) — no need for a separate thread pool
- Set a max execution timeout (e.g., 60 s) using `CompletableFuture.orTimeout()` to prevent hanging
- **`JOB_RUNNING` message type is not yet defined** in `MessageType.java` — add `JOB_RUNNING((byte) 0x06)` as part of this issue
- The `RUNNING` state already exists in `JobStatus` (`ASSIGNED → RUNNING → COMPLETED | FAILED`); the manager must handle `JOB_RUNNING` in `ManagerServer` to call `job.transition(RUNNING)` and keep the state machine consistent
- Without this packet, any timeout/fault-detection logic in a future issue cannot distinguish "assigned but not yet started" from "actively executing"
