# Workers API

The Workers API allows you to monitor the health, capacity, and current load of the worker nodes connected to the Fern-OS cluster.

## Endpoints

### List Workers
`GET /api/v1/workers`

Returns a list of all workers registered in the system.

**Response Body:** `List<WorkerResponse>`

Example:
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "hostname": "worker-1",
    "ipAddress": "192.168.1.10",
    "status": "ONLINE",
    "maxCapacity": 8,
    "activeJobCount": 3,
    "lastHeartbeat": "2026-04-22T16:55:00Z"
  }
]
```

---

### Get Worker Detail
`GET /api/v1/workers/{workerId}`

**Response:** `WorkerResponse`

---

## Response DTO Reference

### WorkerResponse
```json
{
  "id": "uuid",
  "hostname": "string",
  "ipAddress": "string",
  "status": "ONLINE|OFFLINE",
  "maxCapacity": 4,      // Total parallel job slots
  "activeJobCount": 0,   // Currently occupied slots
  "lastHeartbeat": "timestamp"
}
```

---

[Introduction](introduction.md) | [Workflows API](workflows.md) | [Jobs API](jobs.md)
