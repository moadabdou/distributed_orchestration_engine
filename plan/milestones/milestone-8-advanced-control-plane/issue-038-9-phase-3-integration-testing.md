# Issue 038.9: Phase 3 Integration & Testing — API

## Phase
**Phase 3: API — REST Endpoints for Observation & Control**

## Description
Consolidate all Phase 3 components, run comprehensive controller and integration tests, and verify the API with manual Postman/curl tests.

## Scope

### 1. Test Suite

| Test File | What It Tests |
|-----------|---------------|
| `WorkflowControllerTest` | @WebMvcTest — request validation, DTO mapping, error responses |
| `WorkflowApiIntegrationTest` | @SpringBootTest + Testcontainers — full request/response cycle, DB persistence, DAG operations |
| `LegacyJobApiCompatibilityTest` | Verify existing `/jobs` endpoints still work and create auto-workflows correctly |
| `WorkflowErrorHandlingTest` | All error codes and status codes for edge cases |

**Test approach:** Use `@WebMvcTest` for controller-layer tests (mock service) and `@SpringBootTest` for full integration tests.

### 2. Manual API Verification
Use Postman or curl to verify:
1. `POST /api/v1/workflows` — create a workflow with a diamond DAG
2. `GET /api/v1/workflows` — list workflows, verify pagination
3. `GET /api/v1/workflows/{id}/dag` — get the full DAG graph
4. `POST /api/v1/workflows/{id}/execute` — start execution
5. `POST /api/v1/jobs` — verify legacy endpoint still works (creates single-job workflow)
6. Verify error responses for: cycle detection, workflow not editable, workflow already running

### 3. Integration Checklist
- [ ] All controller and integration tests pass
- [ ] Manual API verification completed with Postman/curl
- [ ] Legacy `/jobs` endpoints verified as backward compatible
- [ ] Code review completed
- [ ] Merged to main branch

## Acceptance Criteria
- [ ] All 4 test files implemented and passing
- [ ] Full request/response cycle verified with DB persistence
- [ ] Legacy job API compatibility confirmed
- [ ] Error handling returns correct HTTP status codes and error codes
- [ ] Phase 3 gate met: "REST endpoints for full workflow CRUD + control" working
- [ ] curl/Postman can create → execute → monitor workflows

## Deliverables
```
manager-node/
  src/test/java/com/doe/manager/api/
    WorkflowControllerTest.java
    WorkflowApiIntegrationTest.java
    LegacyJobApiCompatibilityTest.java
    WorkflowErrorHandlingTest.java

docs/
  phase-3-test-report.md          -- summary of test results
  phase-3-api-guide.md            -- Postman/curl verification guide
```

## Dependencies
- Issue 038.8 (Workflow REST API — DTOs & Controller)
- Testcontainers for PostgreSQL integration tests

## Notes
- Each phase is a mergeable increment
- Phase 1-3 can be verified without a frontend
- Phase 3 gate: All tests pass, curl/Postman can create → execute → monitor workflows
