# Issue #017 — Worker Client & Manager Queue Hardening

**Milestone:** 3 — Spring Boot Management Plane  
**Labels:** `engine-core`, `worker-node`, `manager-node`, `priority:medium`  
**Assignee:** —  
**Estimate:** 0.5 day  
**Depends on:** #011  

## Description

Hardening network resilience and manager heap limits:
1. `JobQueue` is currently unbounded, risking `OutOfMemoryError` on massive bursts.
2. `WorkerClient` relies solely on heartbeat write-failures to catch disconnected managers. If TCP connection goes half-open, `socket.read()` blocks forever.

## Acceptance Criteria

- [ ] Bounded capacity (e.g. 10,000) for `JobQueue`, rejecting new jobs when full (HTTP 429).
- [ ] `WorkerClient` configures `socket.setSoTimeout` (e.g. 10 seconds).
- [ ] Catch `SocketTimeoutException` on worker side to verify manager liveliness or disconnect aggressively.
