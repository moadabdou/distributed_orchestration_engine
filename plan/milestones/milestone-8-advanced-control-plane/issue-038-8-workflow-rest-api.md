# Issue 038.8: Workflow REST API — DTOs & Controller

## Phase
**Phase 3: API — REST Endpoints for Observation & Control**

## Description
Implement the REST API endpoints for workflow CRUD and control operations, including request/response DTOs and proper error handling.

## Scope

### 1. REST Endpoints

Base path: `/api/v1/workflows`

| Method | Path | Request Body | Response | Description |
|--------|------|-------------|----------|-------------|
| `POST` | `/workflows` | `CreateWorkflowRequest` | `WorkflowResponse` | Create a workflow with jobs and dependencies |
| `GET` | `/workflows` | — | `Page<WorkflowSummaryResponse>` | List all workflows (paginated, filterable by status) |
| `GET` | `/workflows/{id}` | — | `WorkflowResponse` | Get workflow details |
| `PUT` | `/workflows/{id}` | `UpdateWorkflowRequest` | `WorkflowResponse` | Update workflow definition |
| `DELETE` | `/workflows/{id}` | — | 204 | Delete a workflow |
| `POST` | `/workflows/{id}/execute` | — | `WorkflowResponse` | Start workflow execution |
| `POST` | `/workflows/{id}/pause` | — | `WorkflowResponse` | Pause a running workflow |
| `POST` | `/workflows/{id}/resume` | — | `WorkflowResponse` | Resume a paused workflow |
| `POST` | `/workflows/{id}/reset` | — | `WorkflowResponse` | Reset workflow to DRAFT |
| `GET` | `/workflows/{id}/dag` | — | `DagGraphResponse` | Get the full DAG (nodes + edges) |

### 2. Request/Response DTOs

#### `CreateWorkflowRequest`
```java
public record CreateWorkflowRequest(
    String name,
    List<JobDefinition> jobs,
    List<DependencyEdge> dependencies
) {
    public record JobDefinition(
        String label,
        String payload,
        Long timeoutMs,
        Integer retryCount
    ) {}
    public record DependencyEdge(
        String fromJobLabel,
        String toJobLabel
    ) {}
}
```

**Note:** Dependencies reference jobs by **label** or **array index** (e.g., `jobs[0] → jobs[1]`). The backend resolves these to actual UUIDs.

#### `UpdateWorkflowRequest`
```java
public record UpdateWorkflowRequest(
    String name,
    List<JobDefinition> jobs,
    List<DependencyEdge> dependencies
) {}
```

#### `WorkflowResponse`
```java
public record WorkflowResponse(
    UUID id,
    String name,
    WorkflowStatus status,
    int totalJobs,
    int completedJobs,
    int failedJobs,
    int pendingJobs,
    Instant createdAt,
    Instant updatedAt
) {}
```

#### `WorkflowSummaryResponse` (for list view)
```java
public record WorkflowSummaryResponse(
    UUID id,
    String name,
    WorkflowStatus status,
    int totalJobs,
    Instant createdAt
) {}
```

#### `DagGraphResponse`
```java
public record DagGraphResponse(
    UUID workflowId,
    String workflowName,
    WorkflowStatus workflowStatus,
    List<DagNodeResponse> nodes,
    List<DagEdgeResponse> edges
) {
    public record DagNodeResponse(
        UUID jobId,
        String label,
        int dagIndex,
        JobStatus status,
        String payload,
        String result,
        UUID workerId,
        Instant createdAt,
        Instant updatedAt
    ) {}
    public record DagEdgeResponse(
        UUID sourceJobId,
        UUID targetJobId
    ) {}
}
```

### 3. Error Handling

New error responses for workflow-specific errors:

| HTTP Status | Error Code | Scenario |
|-------------|------------|----------|
| 400 | `DAG_HAS_CYCLE` | Workflow contains a circular dependency |
| 400 | `MISSING_DEPENDENCY` | A dependency references a non-existent job |
| 409 | `WORKFLOW_NOT_EDITABLE` | Attempting to update a RUNNING/COMPLETED/FAILED workflow |
| 409 | `WORKFLOW_ALREADY_RUNNING` | Attempting to execute an already RUNNING workflow |
| 409 | `WORKFLOW_NOT_PAUSED` | Attempting to resume a non-PAUSED workflow |
| 409 | `WORKFLOW_RUNNING` | Attempting to delete a RUNNING workflow |
| 404 | `WORKFLOW_NOT_FOUND` | Workflow ID does not exist |

Implement via `WorkflowApiExceptionHandler` (extend existing global exception handler).

### 4. Legacy API Backward Compatibility

The existing job endpoints continue to work unchanged:

| Method | Path | Behavior |
|--------|------|----------|
| `POST` | `/jobs` | Creates a single-job workflow internally, returns the `JobResponse` (unchanged contract) |
| `GET` | `/jobs` | Returns all jobs across all workflows (paginated) |
| `GET` | `/jobs/{id}` | Returns a single job (unchanged) |
| `POST` | `/jobs/{id}/cancel` | Cancels the job; updates parent workflow state |

## Acceptance Criteria
- [ ] All 10 endpoints implemented and tested
- [ ] DTOs map correctly between requests/responses and domain models
- [ ] Error handling returns correct HTTP status codes and error codes
- [ ] Pagination works on `GET /workflows` (default page size: 20)
- [ ] Sorting by `createdAt` and `updatedAt` supported (default: `createdAt,desc`)
- [ ] Existing `/jobs` endpoints still work and create auto-workflows correctly
- [ ] DAG validation runs on every `POST` and `PUT`

## Deliverables
```
manager-node/
  src/main/java/com/doe/manager/api/dto/
    CreateWorkflowRequest.java
    UpdateWorkflowRequest.java
    WorkflowResponse.java
    WorkflowSummaryResponse.java
    DagGraphResponse.java

  src/main/java/com/doe/manager/api/controller/
    WorkflowController.java

  src/main/java/com/doe/manager/api/exception/
    WorkflowApiExceptionHandler.java   -- or extend existing global handler
```

## Dependencies
- Issue 038.7 (Phase 2 Integration & Testing — Persistence)
- Issue 038.2 (WorkflowManager — Lifecycle Service)
- Existing `/jobs` endpoints from previous milestones

## Notes
- `POST /workflows` should be idempotent if a client retries with the same request. Consider supporting client-provided workflow IDs.
- `GET /workflows` must support pagination since the list could grow large. Default page size: 20.
- Validation: Run `DagValidator` before accepting changes on every `POST` and `PUT`.
