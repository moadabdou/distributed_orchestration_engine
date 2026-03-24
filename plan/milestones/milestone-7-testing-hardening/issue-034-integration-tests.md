#038 — Integration Test Suite

**Milestone:** 6 — Testing & Hardening  
**Labels:** `testing`, `integration`, `priority:high`  
**Assignee:** —  
**Estimate:** 1.5 days  

## Description

Build an integration test suite that programmatically starts the Manager and Worker(s), submits jobs, and validates end-to-end correctness.

### Test Scenarios

| Test | Description |
|------|-------------|
| `testSingleWorkerSingleJob` | 1 worker, 1 job → COMPLETED |
| `testMultipleWorkersMultipleJobs` | 3 workers, 10 jobs → all COMPLETED |
| `testWorkerCrashRecovery` | 2 workers, 5 jobs → kill 1 worker mid-job → job re-queued → COMPLETED |
| `testNoWorkersAvailable` | 0 workers, 3 jobs → jobs stay PENDING, no errors |
| `testJobFailure` | Submit a job designed to throw → status = FAILED |
| `testManagerRestart` | Submit jobs → stop Manager → restart → jobs recovered |

## Acceptance Criteria

- [ ] Test class `EngineIntegrationTest` using JUnit 5
- [ ] Tests start embedded Manager + Workers programmatically (no Docker required)
- [ ] Uses `@Testcontainers` for PostgreSQL or H2 in-memory for test speed
- [ ] Each test isolated: fresh Manager + fresh DB
- [ ] Timeout assertions: jobs complete within expected time window
- [ ] All 6 scenarios pass on CI
- [ ] Tests runnable via `./gradlew integrationTest`
