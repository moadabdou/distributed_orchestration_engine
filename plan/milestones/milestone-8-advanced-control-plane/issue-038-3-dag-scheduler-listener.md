# Issue 038.3: DAG-Aware Job Scheduler & Result Listener

## Phase
**Phase 1: Engine — In-Memory Workflow Management**

## Description
Implement the DAG-aware scheduler that releases jobs to the queue only when their dependencies are satisfied, and the job result listener that updates workflow state on job completion/failure.

## Scope

### 1. DagScheduler
Located in: `manager-node/src/main/java/com/doe/manager/scheduler/DagScheduler.java`

Replaces or wraps the existing `JobScheduler`. Logic:

```
For each RUNNING workflow:
  1. Find all jobs in PENDING status whose dependencies are ALL COMPLETED
  2. Submit those jobs to the existing JobQueue (FIFO)
  3. When a job completes/fails, update the workflow's job state and re-evaluate
  4. If ALL jobs COMPLETED → workflow status = COMPLETED
  5. If ANY job FAILED → workflow status = FAILED (configurable: fail-fast vs continue)
```

**Key design:**
- Maintains a mapping of `workflowId → pendingJobs`
- Only releases jobs to the `JobQueue` when their dependencies are satisfied
- Runs on a timer thread (configurable interval, default: 1000ms)

**Scheduling patterns to support:**
- Linear DAG (A→B→C)
- Fan-out (A→B, C)
- Fan-in (B, C→D)
- Diamond (A→B, C→D)
- Multi-workflow interleaving

### 2. JobResultListener
Located in: `manager-node/src/main/java/com/doe/manager/scheduler/JobResultListener.java`

Listens for job completion/failure events (existing callback mechanism) and:
- Updates the owning workflow's internal job state
- Triggers the `DagScheduler` to re-evaluate ready jobs
- Updates workflow-level status if terminal state reached (COMPLETED/FAILED)

### 3. Configuration
Add to `application.yml` or `.env`:
```yaml
doe:
  workflow:
    scheduler-interval-ms: 1000    # how often DagScheduler polls for ready jobs
    fail-fast: true                # if one job fails, fail the whole workflow immediately
    max-concurrent-jobs-per-workflow: 10  # limit parallelism per workflow
```

## Acceptance Criteria
- [ ] `DagScheduler` correctly schedules jobs only when dependencies are met
- [ ] Linear, fan-out, fan-in, and diamond DAG patterns work correctly
- [ ] Multi-workflow interleaving doesn't cause conflicts
- [ ] `JobResultListener` triggers workflow state updates on job completion/failure
- [ ] Terminal states (COMPLETED/FAILED) reached correctly
- [ ] `DagSchedulerTest` covers all scheduling patterns
- [ ] `JobResultListenerTest` covers completion and failure scenarios

## Deliverables
```
manager-node/
  src/main/java/com/doe/manager/scheduler/
    DagScheduler.java
    JobResultListener.java
  src/test/java/com/doe/manager/scheduler/
    DagSchedulerTest.java
    JobResultListenerTest.java
```

## Dependencies
- Issue 038.2 (WorkflowManager — Lifecycle Service)
- Existing `JobQueue` and `JobExecutor` infrastructure
- Existing job completion callback mechanism

## Notes
- Use mocks for `JobQueue` and `JobExecutor` in tests
- Verify scheduler behavior by inspecting which jobs get queued and when
- Thread safety: scheduler runs on timer thread, API calls come from HTTP threads
