# Milestone 6: Dockerization & DevOps Infrastructure

## Overview

Containerize every component of the platform — Spring Boot Manager, Java Workers, TypeScript Dashboard, and PostgreSQL — and orchestrate them with Docker Compose for one-command deployment.

After this milestone, anyone can clone the repo and run `docker-compose up --scale worker=5` to spin up the entire distributed system.

## Goals

1. **Manager Dockerfile** — Multi-stage build: compile Spring Boot fat JAR → run on slim JRE 21 image.
2. **Worker Dockerfile** — Multi-stage build: compile worker JAR → run on slim JRE 21 image.
3. **Dashboard Dockerfile** — Multi-stage build: `npm run build` → serve static files via Nginx.
4. **Docker Compose** — Define all services, internal network, volumes, environment variables.
5. **Scaling** — Workers scalable via `--scale worker=N`, connecting dynamically to the Manager.

## Architecture

```
docker-compose.yml
├── postgres       (PostgreSQL 15, persistent volume)
├── manager        (Spring Boot, depends_on: postgres)
├── dashboard      (Nginx, depends_on: manager)
└── worker         (Java 21, depends_on: manager, scalable)
    └── --scale worker=N
```

All services share an internal Docker bridge network (`orchestration-net`).

## Success Criteria

- [ ] `docker-compose up -d` starts all services with zero manual configuration
- [ ] `docker-compose up -d --scale worker=5` runs 5 independent worker instances
- [ ] Dashboard accessible at `http://localhost:3000`
- [ ] Manager API accessible at `http://localhost:8080/api/v1/`
- [ ] Workers auto-register with Manager using Docker DNS (`manager:9090`)
- [ ] Data survives `docker-compose down` / `up` cycles (PostgreSQL volume)
- [ ] Total image sizes optimized (JRE slim, Alpine Nginx, no build tools in final images)

## Dependencies

- Milestones 1–4 complete
- Docker Engine 24+ and Docker Compose v2

## Estimated Effort

**3–4 working days**
