# Issue 038.7: Phase 2 Integration & Testing — Persistence

## Phase
**Phase 2: Persistence — DB Schema + Recovery**

## Description
Consolidate all Phase 2 components, run comprehensive integration tests with Testcontainers, and verify workflow recovery on app restart.

## Scope

### 1. Test Suite

| Test File | What It Tests |
|-----------|---------------|
| `WorkflowRepositoryTest` | @DataJpaTest — CRUD, status queries, ordering |
| `JobDependencyRepositoryTest` | @DataJpaTest — edge CRUD, cascade deletes, constraint violations |
| `WorkflowRecoveryServiceTest` | @SpringBootTest with Testcontainers — mock DB data, verify recovery logic and status transitions |
| `WorkflowPersistenceListenerTest` | Unit test — verifies DB writes match engine events |
| `EndToEndWorkflowTest` | @SpringBootTest — register workflow → execute → jobs complete → verify DB state → restart app → verify recovery |

**Test approach:** Use **Testcontainers** (PostgreSQL) for integration tests. This ensures Flyway migrations run and JPA entities map correctly.

### 2. End-to-End Scenario
The `EndToEndWorkflowTest` should verify:
1. Create a workflow with a diamond DAG (A→B, C→D)
2. Execute the workflow
3. Verify DB state: workflows table has RUNNING status, jobs have correct statuses
4. Simulate app restart
5. Verify recovery: workflow is restored as PAUSED, jobs retain their statuses
6. Resume the workflow
7. Verify workflow reaches COMPLETED status in DB

### 3. Integration Checklist
- [ ] All unit and integration tests pass
- [ ] Flyway migrations apply cleanly on fresh and existing databases
- [ ] Recovery on app restart verified
- [ ] Code review completed
- [ ] Merged to main branch

## Acceptance Criteria
- [ ] All 5 test files implemented and passing
- [ ] End-to-end test verifies full persistence + recovery cycle
- [ ] Testcontainers used for all DB-dependent tests
- [ ] Phase 2 gate met: "DB schema + recovery + state sync" working
- [ ] Restart app → workflows recovered correctly

## Deliverables
```
(All test files from previous issues)

plus:

manager-node/
  src/test/java/com/doe/manager/workflow/
    EndToEndWorkflowTest.java

docs/
  phase-2-test-report.md          -- summary of test results
```

## Dependencies
- Issue 038.5 (Database Schema & JPA Entities for Workflows)
- Issue 038.6 (Workflow Recovery Service & Persistence Listener)
- Testcontainers PostgreSQL image

## Notes
- Each phase is a mergeable increment
- Phase 1-3 can be verified without a frontend
- Phase 2 gate: All tests pass, restart app → workflows recovered correctly
