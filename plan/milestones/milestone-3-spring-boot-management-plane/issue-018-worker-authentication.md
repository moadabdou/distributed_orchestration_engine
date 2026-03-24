# Issue #018 — Worker Authentication & Security

**Milestone:** 3 — Spring Boot Management Plane  
**Labels:** `security`, `manager-node`, `worker-node`, `priority:medium`  
**Assignee:** —  
**Estimate:** 0.5 day  
**Depends on:** #011  

## Description

Currently, any TCP client can connect, send `REGISTER_WORKER`, and consume jobs from the manager without verification. Implement a shared secret or JWT-based authentication handshake so that only authorized workers can join the cluster.

## Acceptance Criteria

- [ ] `REGISTER_WORKER` message structured to include an `auth_token`.
- [ ] Manager Server requires a matching valid token (configurable via Spring properties) during registration.
- [ ] Unauthorized connections are immediately rejected and sockets closed.
- [ ] Worker node accepts `auth_token` via CLI argument/environment variable.
