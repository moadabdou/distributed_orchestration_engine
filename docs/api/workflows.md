# Workflows API

The Workflows API allows you to manage the lifecycle of workflows, define DAGs (Directed Acyclic Graphs), and monitor execution progress.

## Endpoints

### Create Workflow
`POST /api/v1/workflows`

Creates a new workflow definition. Note that `payload` in `jobs` is a JSON-encoded string.

**Request Body:**
```json
{
  "name": "data-pipeline",
  "jobs": [
    {
      "label": "gen-data",
      "type": "python",
      "payload": "{\"type\":\"python\",\"script\":\"print('gen')\"}",
      "timeoutMs": 60000,
      "retryCount": 3
    }
  ],
  "dependencies": [
    {
      "fromJobLabel": "gen-data",
      "toJobLabel": "process-data"
    }
  ],
  "dataDependencies": []
}
```

**Response:** `WorkflowResponse` (Status 201 Created)

---

### List Workflows
`GET /api/v1/workflows`

Returns a paginated list of workflows.

**Query Parameters:**
- `page` (int, default: 0)
- `size` (int, default: 20)
- `status` (string, optional): `DRAFT`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`

**Response:** `Page<WorkflowSummaryResponse>`

---

### Get Workflow Detail
`GET /api/v1/workflows/{id}`

**Response:** `WorkflowResponse`

---

### Update Workflow
`PUT /api/v1/workflows/{id}`

Replaces the definition. Workflow must be in `DRAFT` status.

**Request Body:** Same as Create Workflow.
**Response:** `WorkflowResponse`

---

### Delete Workflow
`DELETE /api/v1/workflows/{id}`

**Response:** 204 No Content

---

### Execution Controls

| Endpoint | Description | Response |
|----------|-------------|----------|
| `POST /{id}/execute` | Starts execution (DRAFT → RUNNING) | `WorkflowResponse` |
| `POST /{id}/pause` | Pauses execution (RUNNING → PAUSED) | `WorkflowResponse` |
| `POST /{id}/resume` | Resumes execution (PAUSED → RUNNING) | `WorkflowResponse` |
| `POST /{id}/reset` | Resets to DRAFT status | `WorkflowResponse` |
| `DELETE /{id}/xcom` | Clears XCom history for the workflow | 204 No Content |

---

### DAG and Jobs

#### Get DAG Graph
`GET /api/v1/workflows/{id}/dag`

Returns nodes and edges (both control flow and data flow).

**Response Body:**
```json
{
  "workflowId": "uuid",
  "workflowName": "string",
  "status": "DRAFT|RUNNING|...",
  "nodes": [
    {
      "jobId": "uuid",
      "label": "string", // Display label (jobLabel or fallback)
      "dagIndex": 0,
      "status": "PENDING|RUNNING|...",
      "payload": "json-string",
      "result": "string",
      "workerId": "uuid",
      "timeoutMs": 60000,
      "jobLabel": "string", // Original label from definition
      "createdAt": "timestamp",
      "updatedAt": "timestamp"
    }
  ],
  "edges": [
    { "fromJobId": "uuid", "toJobId": "uuid" } // Control dependencies
  ],
  "dataEdges": [
    { "fromJobId": "uuid", "toJobId": "uuid" } // Data dependencies
  ]
}
```

#### Get Workflow Jobs
`GET /api/v1/workflows/{id}/jobs`

**Response:** `Page<JobResponse>`

#### Get Job by Label
`GET /api/v1/workflows/{id}/jobs/{label}`

**Response:** `JobResponse`

---

## Job Payload Structures

The `payload` field in any job definition must be a JSON string. It **must** contain a `type` field that matches the job's `type` field at the root level.

### Python Job (`type: "python"`)
```json
{
  "type": "python",
  "script": "import os\nprint('Hello')",
  "args": ["--mode", "fast"],       // Optional
  "env": {"DEBUG": "true"},        // Optional
  "venv": "/path/to/venv",         // Optional
  "conda_env": "my-env"            // Optional
}
```

### Bash Job (`type: "bash"`)
```json
{
  "type": "bash",
  "script": "ls -la && echo done"
}
```

### Sleep Job (`type: "sleep"`)
```json
{
  "type": "sleep",
  "ms": 5000
}
```

### Echo Job (`type: "echo"`)
```json
{
  "type": "echo",
  "data": "any string content"
}
```

---

## Response DTO Reference

### WorkflowResponse
```json
{
  "id": "uuid",
  "name": "string",
  "status": "DRAFT|RUNNING|PAUSED|COMPLETED|FAILED",
  "totalJobs": 10,
  "completedJobs": 2,
  "failedJobs": 0,
  "pendingJobs": 8,
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

### WorkflowSummaryResponse
```json
{
  "id": "uuid",
  "name": "string",
  "status": "DRAFT|RUNNING|...",
  "totalJobs": 10,
  "createdAt": "timestamp"
}
```

---

[Introduction](introduction.md) | [Jobs API](jobs.md) | [Workers API](workers.md)
