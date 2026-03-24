# Issue #015 — Manager Startup Recovery from Database

**Milestone:** 3 — Spring Boot Management Plane  
**Labels:** `manager-node`, `fault-tolerance`, `persistence`, `priority:critical`  
**Assignee:** —  
**Estimate:** 0.5 day  
**Depends on:** #014  

## Description

When the Manager crashes and restarts, it must recover orphaned jobs from the database.

### Recovery Logic (on startup)

```
1. Query: SELECT * FROM jobs WHERE status IN ('ASSIGNED', 'RUNNING')
2. For each → transition to PENDING, clear worker_id
3. Enqueue into JobQueue
4. Mark all workers in DB as OFFLINE (they disconnected during crash)
5. Log: "Recovered N orphaned jobs on startup"
```

## Acceptance Criteria

- [ ] `StartupRecoveryService` with `@PostConstruct` or `ApplicationReadyEvent` listener
- [ ] Queries DB for jobs in `ASSIGNED` or `RUNNING` state
- [ ] Resets them to `PENDING`, clears `worker_id`, updates `updated_at`
- [ ] Enqueues recovered jobs into `JobQueue` (ensuring in-memory queue state is perfectly rebuilt from persistent storage)
- [ ] All workers in DB set to `OFFLINE` on startup (stale connections)
- [ ] Logged with count: `"Startup recovery: reset N jobs to PENDING"`
- [ ] **Test:** Submit 5 jobs → kill Manager mid-execution → restart → jobs resume and complete
