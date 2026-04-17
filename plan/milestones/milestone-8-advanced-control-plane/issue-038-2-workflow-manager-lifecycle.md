# Issue 038.2: WorkflowManager — Lifecycle Service

## Phase
**Phase 1: Engine — In-Memory Workflow Management**

## Description
Implement the core `WorkflowManager` service that manages workflow lifecycle operations in memory with thread-safe state management.

## Scope

### WorkflowManager Service
Located in: `manager-node/src/main/java/com/doe/manager/workflow/WorkflowManager.java`

**Operations:**
- `registerWorkflow(Workflow)` — validates DAG, stores in memory, sets status to DRAFT
- `deleteWorkflow(UUID)` — removes from memory (only if not RUNNING)
- `updateWorkflow(UUID, Workflow)` — replaces workflow definition (only if editable; recalculates topological order)
- `executeWorkflow(UUID)` — transitions DRAFT → RUNNING, begins scheduling eligible jobs
- `pauseWorkflow(UUID)` — transitions RUNNING → PAUSED, stops scheduling new jobs
- `resumeWorkflow(UUID)` — transitions PAUSED → RUNNING, resumes scheduling
- `resetWorkflow(UUID)` — transitions any non-RUNNING state back to DRAFT, resets all job statuses to PENDING
- `getWorkflow(UUID)` — returns workflow snapshot
- `listWorkflows()` — returns all workflows (optionally filtered by status)

### Thread Safety
- Use `ConcurrentHashMap<UUID, Workflow>` for storage
- Synchronize state mutations with `ReentrantReadWriteLock`
- Prevent race conditions between API calls, scheduler thread, and listener callbacks

### Lifecycle State Machine
Enforce valid transitions:
```
DRAFT → RUNNING (execute)
DRAFT → DELETED (delete)
DRAFT → DRAFT (update)
RUNNING → PAUSED (pause)
PAUSED → RUNNING (resume)
PAUSED → DRAFT (reset)
FAILED → DRAFT (reset)
COMPLETED → DRAFT (reset)
```

**Editing rules:**
- Allowed when: DRAFT or PAUSED
- Blocked when: RUNNING, COMPLETED, or FAILED

## Acceptance Criteria
- [ ] All lifecycle operations implemented and thread-safe
- [ ] State machine enforces valid transitions (rejects invalid ones)
- [ ] `WorkflowManagerTest` covers all operations and edge cases
- [ ] `WorkflowStatusMachineTest` validates all valid/invalid transitions
- [ ] Concurrent access handled without race conditions

## Deliverables
```
manager-node/
  src/main/java/com/doe/manager/workflow/
    WorkflowManager.java
  src/test/java/com/doe/manager/workflow/
    WorkflowManagerTest.java
    WorkflowStatusMachineTest.java
```

## Dependencies
- Issue 038.1 (Workflow Domain Models & DAG Validator)
- Existing `JobQueue` and `JobExecutor` (to be wrapped later)

## Notes
- No persistence yet — workflows lost on restart (acceptable for Phase 1)
- Legacy compatibility: existing `JobScheduler` (FIFO) can coexist; `DagScheduler` will wrap it later

## Execution Strategy — Latch-Based DAG Execution

### Latch Counter Design (for Issue 038.3 — DagScheduler)
When the DagScheduler is implemented, it will use a **latch-based approach** to determine job readiness:

**Mechanism:**
1. Each `WorkflowJob` gets a latch counter = number of dependencies (in-degree)
2. When a dependency completes, decrement the latch of all its dependents
3. When latch reaches 0 → job is eligible → submit to `JobQueue`
4. Initial seed: enqueue all root jobs (latch == 0) when workflow starts

**Data Structures (in DagScheduler):**
```java
Map<UUID, AtomicInteger> latchCounters;       // jobId → remaining deps
Map<UUID, List<UUID>> dependents;             // jobId → list of jobs that depend on it
```

**Why this works for the current architecture:**
- The existing `JobScheduler` uses a **single central queue** consumed by **one virtual thread**
- Latch-based execution naturally feeds into this single queue — no work-stealing or per-worker queues needed yet
- Atomic operations (`AtomicInteger.decrementAndGet()`) ensure thread-safety across the scheduler thread, API threads, and callback threads
- This pattern scales to thousands of nodes and is used by PyTorch's autograd engine, Flink, and Ray

**Scalability notes for future optimization:**
- Current single-queue architecture is sufficient for Phase 1
- Future milestones can upgrade to per-worker queues + work-stealing if queue contention becomes a bottleneck
- The latch primitive remains the same regardless of queue architecture — only the dispatch mechanism changes

**Execution flow (DagScheduler):**
```
onWorkflowExecute(workflowId):
    compute in-degree for each job
    latch[jobId] = inDegree(jobId)
    for each job with latch == 0:
        jobQueue.enqueue(job)

onJobCompleted(jobId):
    for each dependent in dependents[jobId]:
        if latch[dependent].decrementAndGet() == 0:
            jobQueue.enqueue(dependent)
```
