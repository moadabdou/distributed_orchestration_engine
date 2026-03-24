#025 — Typed API Client Service

**Milestone:** 4 — Observability Dashboard  
**Labels:** `frontend`, `api`, `priority:high`  
**Assignee:** —  
**Estimate:** 0.5 day  
**Depends on:** #020  

## Description

Create a typed HTTP client layer that wraps all API calls to the Spring Boot backend with full TypeScript type safety.

### Type Definitions

```typescript
// types/api.ts

interface Job {
  id: string;
  status: 'PENDING' | 'ASSIGNED' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  payload: Record<string, unknown>;
  result: string | null;
  workerId: string | null;
  createdAt: string;
  updatedAt: string;
}

interface Worker {
  id: string;
  hostname: string;
  ipAddress: string;
  status: 'IDLE' | 'BUSY' | 'OFFLINE';
  lastHeartbeat: string;
}

interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

interface CreateJobRequest {
  payload: Record<string, unknown>;
}
```

## Acceptance Criteria

- [ ] `api/client.ts` — Axios or Fetch instance with base URL config and error interceptor
- [ ] `api/jobs.ts` — `getJobs(page, size, status?)`, `getJob(id)`, `createJob(payload)` — all typed
- [ ] `api/workers.ts` — `getWorkers()` — returns `Worker[]`
- [ ] All functions return typed promises (no `any`)
- [ ] Error handling: network errors surfaced as typed `ApiError` objects
- [ ] Unit tests with mocked HTTP: verify correct URL construction and response parsing
