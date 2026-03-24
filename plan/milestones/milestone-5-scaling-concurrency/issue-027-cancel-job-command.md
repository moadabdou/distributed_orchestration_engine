#031 — Manager-Initiated Cancel Job Command

**Milestone:** 5 — Scaling & Concurrency  
**Labels:** `manager-node`, `worker-node`, `priority:high`

## Description

Sometimes a job enters an inherently unpredictable state, infinite loop, or resource stagnation. Relying purely on the configured `timeoutMs` to save the job might be too slow. The network protocol requires a new real-time `CANCEL_JOB` envelope.

## Acceptance Criteria

- [ ] Add `CANCEL_JOB` to the `MessageType` enum.
- [ ] Add an API to `ManagerServer` (or REST API via Spring) to trigger `sendCancelJob(UUID workerId, UUID jobId)`.
- [ ] Ensure the worker client parses `CANCEL_JOB`, retrieves the corresponding `Future<?>` mapped in `activeJobs` (from Issue #025), and invokes `.cancel(true)`.
- [ ] The worker must capture the `InterruptedException` resulting from the `.cancel(true)` command and send an emergency `JOB_RESULT` reporting a `FAILED / CANCELLED` conclusion to gracefully clear state on both nodes.
