#037 — Structured Logging with MDC Correlation IDs

**Milestone:** 6 — Testing & Hardening  
**Labels:** `observability`, `logging`, `priority:high`  
**Assignee:** —  
**Estimate:** 0.5 day  

## Description

Configure SLF4J + Logback across all Java modules with structured JSON logging and MDC-based correlation IDs.

### Log Format

```json
{
  "timestamp": "2025-03-20T10:15:30Z",
  "level": "INFO",
  "logger": "ManagerServer",
  "jobId": "abc-123",
  "workerId": "def-456",
  "message": "Job assigned to worker"
}
```

### MDC Fields

| Field | Set When |
|-------|----------|
| `workerId` | Worker connection handler enters scope |
| `jobId` | Job processing begins |
| `requestId` | HTTP API request received |

## Acceptance Criteria

- [ ] `logback-spring.xml` configured with JSON encoder (Logstash Logback Encoder)
- [ ] Console output in structured JSON format
- [ ] MDC set in worker connection handler with `workerId`
- [ ] MDC set in job processing with `jobId`
- [ ] MDC set in REST controllers with `requestId` (UUID per HTTP request via filter)
- [ ] All existing log statements include contextual information from MDC
- [ ] Log levels configurable via `application.yml` (default: INFO, DEBUG for engine-core in dev)
