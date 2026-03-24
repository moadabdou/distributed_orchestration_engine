#034 — Dockerfile for Java Worker

**Milestone:** 5 — Dockerization & DevOps  
**Labels:** `devops`, `docker`, `worker-node`, `priority:high`  
**Assignee:** —  
**Estimate:** 0.5 day  
**Depends on:** #004  

## Description

Create a multi-stage Dockerfile for the Java Worker node. Workers must connect to the Manager using Docker service DNS.

### Key Configuration

- Manager host resolved via Docker Compose service name: `manager`
- Connection config via environment variables: `MANAGER_HOST=manager`, `MANAGER_PORT=9090`

## Acceptance Criteria

- [ ] Multi-stage build: JDK compile → JRE-slim runtime
- [ ] Final image size < 200 MB
- [ ] `MANAGER_HOST` and `MANAGER_PORT` configurable via env vars (defaults: `localhost`, `9090`)
- [ ] Worker reads env vars on startup for connection target
- [ ] Container starts, connects to Manager, registers, and begins heartbeat loop
- [ ] `docker build -t orchestration-worker .` succeeds
- [ ] Scalable: multiple instances of same image connect independently (unique UUIDs)
