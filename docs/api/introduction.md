# Fern-OS API Documentation

Welcome to the Fern-OS REST API documentation. This API allows you to manage workflows, monitor workers, and control job execution programmatically.

## Base URL
The API is exposed by the **Manager Node**. By default, it is available at:
`http://<manager-host>:8080/api/v1`

## Common Response Formats

### Pagination (Spring Data Page)
All list endpoints return a paginated response using the standard Spring Data structure.

**Response Structure:**
```json
{
  "content": [ ... ], // Array of result items
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "sorted": true, "unsorted": false, "empty": false },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "last": true,
  "totalPages": 1,
  "totalElements": 5,
  "size": 20,
  "number": 0,
  "sort": { "sorted": true, "unsorted": false, "empty": false },
  "first": true,
  "numberOfElements": 5,
  "empty": false
}
```

### Error Body
When an error occurs (4xx or 5xx status), the API returns a structured JSON error body.

**Error Structure:**
```json
{
  "timestamp": "2026-04-22T16:55:54.123Z",
  "status": 404,
  "error": "Not Found",
  "code": "WORKFLOW_NOT_FOUND", // Custom error code
  "message": "Workflow not found: <uuid>"
}
```

## Job Payload Concept
Every job has a `payload` field. This is not a simple string, but a **JSON-encoded string** that must contain a `type` property. The content of the payload depends on the job type.

Example Payload for a Python Job:
```json
{
  "type": "python",
  "script": "print('hello world')",
  "args": ["arg1"],
  "env": {"KEY": "VALUE"}
}
```

## Authentication
The REST API currently operates without explicit HTTP authentication in the default configuration. However, the internal orchestration protocol uses JWT-based authentication for job-to-manager signaling.
