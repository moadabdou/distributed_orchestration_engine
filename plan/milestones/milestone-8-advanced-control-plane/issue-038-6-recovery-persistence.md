# Issue 038.6: Workflow Recovery Service & Persistence Listener

## Phase
**Phase 2: Persistence — DB Schema + Recovery**

## Description
Implement workflow recovery on startup (load workflows from DB and restore state) and event-driven persistence that syncs engine events to the database.

## Scope

### 1. WorkflowRecoveryService
Located in: `manager-node/src/main/java/com/doe/manager/workflow/WorkflowRecoveryService.java`

On `@PostConstruct` or `ApplicationReadyEvent`:

```
1. Load all workflows from DB ordered by created_at
2. For each workflow:
   a. Load all jobs with workflow_id = workflow.id
   b. Load all dependencies for those jobs
   c. Reconstruct Workflow domain object
   d. Apply recovery rules:
      - RUNNING workflows → transition to PAUSED (safe default: don't auto-resume)
      - FAILED workflows → keep as FAILED (user decides whether to retry)
      - COMPLETED workflows → keep as COMPLETED
      - DRAFT/PAUSED workflows → keep as-is
   e. Register reconstructed workflow in WorkflowManager
3. Log recovery summary: "Recovered N workflows (X DRAFT, Y PAUSED, Z FAILED, W COMPLETED)"
```

**Design choice:** RUNNING workflows are recovered as PAUSED to avoid accidentally re-executing jobs on restart. The user must explicitly resume.

### 2. WorkflowPersistenceListener
Located in: `manager-node/src/main/java/com/doe/manager/workflow/WorkflowPersistenceListener.java`

Event-driven persistence — listens to domain events from `WorkflowManager` and writes to DB:

| WorkflowManager Action | DB Side Effect |
|------------------------|----------------|
| `registerWorkflow` | INSERT into `workflows`, INSERT jobs, INSERT dependencies |
| `deleteWorkflow` | DELETE from `workflows` (cascades) |
| `updateWorkflow` | UPDATE `workflows`, UPDATE jobs, UPDATE dependencies (delete + re-insert) |
| `executeWorkflow` | UPDATE `workflows.status = RUNNING`, UPDATE jobs.status |
| `pauseWorkflow` | UPDATE `workflows.status = PAUSED` |
| `resumeWorkflow` | UPDATE `workflows.status = RUNNING` |
| `resetWorkflow` | UPDATE `workflows.status = DRAFT`, UPDATE all jobs.status = PENDING |
| `JobResultListener` (job done) | UPDATE job.status, UPDATE `workflows.updated_at`, possibly UPDATE `workflows.status` |

**Recommendation:** Use event-driven approach (Option B) to keep engine logic decoupled from persistence, making testing easier.

### 3. Configuration
```yaml
doe:
  workflow:
    recovery-mode: PAUSED_ON_RESTART    # PAUSED or RESUME_AUTO
```

## Acceptance Criteria
- [ ] `WorkflowRecoveryService` loads workflows from DB on startup
- [ ] RUNNING workflows are recovered as PAUSED
- [ ] Recovery is idempotent (multiple restarts don't duplicate workflows)
- [ ] `WorkflowPersistenceListener` writes to DB on every engine mutation
- [ ] `WorkflowRecoveryServiceTest` verifies recovery logic with Testcontainers
- [ ] `WorkflowPersistenceListenerTest` verifies DB writes match engine events
- [ ] Structured logging for recovery summary

## Deliverables
```
manager-node/
  src/main/java/com/doe/manager/workflow/
    WorkflowRecoveryService.java
    WorkflowPersistenceListener.java

  src/test/java/com/doe/manager/workflow/
    WorkflowRecoveryServiceTest.java
    WorkflowPersistenceListenerTest.java
```

## Dependencies
- Issue 038.5 (Database Schema & JPA Entities for Workflows)
- Issue 038.2 (WorkflowManager — Lifecycle Service)
- Issue 038.3 (DAG-Aware Job Scheduler & Result Listener)
- Event/domain event infrastructure from engine-core

## Notes
- Recovery idempotency: use workflow `id` as the key
- Consider adding a `WHERE status != 'COMPLETED'` filter if completed workflows can be archived (future optimization)
- Persist job status updates immediately (not batched) to avoid losing in-progress results on crash
