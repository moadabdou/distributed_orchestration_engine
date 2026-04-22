# Jobs API

The Jobs API allows you to submit standalone jobs, monitor job status, and retrieve execution logs. While most jobs are part of a Workflow, they can also be interacted with directly as independent units of work.

## Endpoints

### Submit Job
`POST /api/v1/jobs`

Submits a new standalone job.

**Request Body:**
```json
{
  "label": "standalone-python-task",
  "payload": "{\"type\":\"python\", \"script\":\"print('hello')\"}",
  "timeoutMs": 30000
}
```

**Response Body:** `JobResponse` (Status 201 Created)

---

### List Jobs
`GET /api/v1/jobs`

**Query Parameters:**
- `page` (int, default: 0)
- `size` (int, default: 20)
- `status` (string, optional): `PENDING`, `ASSIGNED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`, `SKIPPED`

**Response:** `Page<JobResponse>`

---

### Get Job Detail
`GET /api/v1/jobs/{id}`

**Response:** `JobResponse`

---

### Cancel Job
`POST /api/v1/jobs/{id}/cancel`

Cancels a job if it is not in a terminal state.

**Response:** `JobResponse` (reflecting status `CANCELLED`)

---

### Retry Job
`POST /api/v1/jobs/{id}/retry`

Retries a `FAILED` or `CANCELLED` job. This increments the `retryCount` and moves the job back to `PENDING`.

**Response:** `JobResponse` (reflecting status `PENDING`)

---

## Logs API
Base path: `/api/v1/logs/jobs`

### Get Job Logs (HTML)
`GET /api/v1/logs/jobs/{id}`

Returns a styled HTML view of the logs.

**Query Parameters:**
- `start` (int, optional): Starting line index (0-based).
- `length` (int, optional): Max number of lines to retrieve.

**Response Header:** `Content-Type: text/html`

### Get Job Logs (Raw)
`GET /api/v1/logs/jobs/{id}/raw`

Returns the raw log lines.

**Query Parameters:**
- `start` (int, optional): Starting line index (0-based).
- `length` (int, optional): Max number of lines to retrieve.

**Response Header:** `Content-Type: text/plain`

---

## Response DTO Reference

### JobResponse
```json
{
  "id": "uuid",
  "status": "PENDING|ASSIGNED|RUNNING|COMPLETED|FAILED|CANCELLED|SKIPPED",
  "label": "string",       // The job label
  "payload": "string",     // The JSON-encoded payload string
  "result": "string",      // Final output/error from worker
  "workerId": "uuid",      // ID of assigned worker (null if PENDING)
  "workflowId": "uuid",    // ID of parent workflow (null for standalone)
  "retryCount": 0,         // Number of times retried
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

---

[Introduction](introduction.md) | [Workflows API](workflows.md) | [Workers API](workers.md)
