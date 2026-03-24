#036 — Docker Compose Orchestration File

**Milestone:** 5 — Dockerization & DevOps  
**Labels:** `devops`, `docker`, `infrastructure`, `priority:critical`  
**Assignee:** —  
**Estimate:** 1 day  
**Depends on:** #025, #026, #027  

## Description

Create the `docker-compose.yml` that defines all services, networking, volumes, health checks, and startup ordering.

### Service Definitions

```yaml
services:
  postgres:
    image: postgres:15-alpine
    volumes: [pgdata:/var/lib/postgresql/data]
    environment:
      POSTGRES_DB: orchestration
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: ${DB_PASSWORD:-secret}
    healthcheck:
      test: pg_isready -U admin
    
  manager:
    build: { context: ., dockerfile: manager-node/Dockerfile }
    depends_on:
      postgres: { condition: service_healthy }
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/orchestration
    
  dashboard:
    build: { context: ., dockerfile: dashboard/Dockerfile }
    depends_on: [manager]
    ports: ["3000:80"]
    
  worker:
    build: { context: ., dockerfile: worker-node/Dockerfile }
    depends_on:
      manager: { condition: service_healthy }
    environment:
      MANAGER_HOST: manager
      MANAGER_PORT: 9090
    deploy:
      replicas: 3
```

## Acceptance Criteria

- [ ] `docker-compose up -d` starts all 5 services (postgres, manager, dashboard, 3 workers)
- [ ] `docker-compose up -d --scale worker=5` scales to 5 workers
- [ ] Services start in correct order: postgres → manager → (dashboard, workers)
- [ ] PostgreSQL data survives `docker-compose down` / `up` (named volume `pgdata`)
- [ ] All services share `orchestration-net` bridge network
- [ ] `.env.example` with all configurable environment variables documented
- [ ] Manager health check gates worker startup (workers don't start until Manager is ready)
- [ ] `docker-compose logs -f worker` shows all worker instances registering
- [ ] `docker-compose down -v` tears down everything including volumes (clean slate)
