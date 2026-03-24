# Issue #010 — Crash Recovery & Job Re-Queuing

**Milestone:** 2 — Job Scheduling & Fault Tolerance  
**Labels:** `manager-node`, `fault-tolerance`, `priority:critical`  
**Assignee:** —  
**Estimate:** 1 day  
**Depends on:** #005, #007, #009  

## Description

When a worker dies (detected by the heartbeat monitor from M1), any jobs in `ASSIGNED` or `RUNNING` state on that worker must be automatically re-queued to `PENDING` and placed at the **front** of the job queue.

### Recovery Flow

```
HeartbeatMonitor detects dead worker
    → WorkerRegistry.remove(workerId)
    → JobRegistry.findByWorker(workerId)
        → For each job in ASSIGNED or RUNNING:
            job.transition(PENDING)
            job.assignedWorkerId = null
            jobQueue.requeue(job)  // add to front
    → Log: "Recovered N jobs from dead worker <UUID>"
```

## Acceptance Criteria

- [ ] `HeartbeatMonitor` calls a `WorkerDeathHandler` callback when a worker is marked `DEAD`
- [ ] `WorkerDeathHandler` queries `JobRegistry` for jobs belonging to the dead worker
- [ ] All `ASSIGNED` / `RUNNING` jobs are transitioned back to `PENDING`
- [ ] Re-queued jobs go to the **front** of the queue (priority re-assignment)
- [ ] Worker ID cleared from re-queued jobs
- [ ] **Chaos test:** Start 3 workers, submit 20 jobs, kill 1 worker mid-execution → all 20 jobs eventually reach `COMPLETED`
- [ ] **Edge case:** Worker dies with no jobs assigned → no-op, no errors

## Technical Notes

- This is the single most important reliability feature — get it right
- Use the Observer pattern: `HeartbeatMonitor` → `WorkerDeathHandler` → avoids tight coupling
- Consider adding a `retryCount` to `Job` to prevent infinite re-queue loops (cap at 3 retries, then `FAILED`)

## Known Flaws & Oversights (Added Post-Analysis)

- **Unbounded Queue:** `JobQueue` uses an unbounded `ConcurrentLinkedDeque`. Submitting too many jobs can lead to `OutOfMemoryError`.
- **At-Least-Once Execution:** If a worker finishes a job but crashes right before sending `JOB_RESULT`, the job will be re-executed. This requires tasks to be idempotent.
- **Worker Hangs:** The `JobTimeoutMonitor` recovers jobs that take too long, but `WorkerClient` relies solely on heartbeat write failures. Silent network drops might cause it to hang on read.
