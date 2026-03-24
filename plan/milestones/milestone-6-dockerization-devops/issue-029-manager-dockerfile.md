#033 — Dockerfile for Spring Boot Manager

**Milestone:** 5 — Dockerization & DevOps  
**Labels:** `devops`, `docker`, `manager-node`, `priority:high`  
**Assignee:** —  
**Estimate:** 0.5 day  
**Depends on:** #011  

## Description

Create a multi-stage Dockerfile for the Spring Boot Manager application.

### Dockerfile Structure

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY . .
RUN ./gradlew :manager-node:bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/manager-node/build/libs/*.jar app.jar
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Acceptance Criteria

- [ ] Multi-stage build: JDK for compile, JRE-slim for runtime
- [ ] Final image size < 300 MB
- [ ] Exposes both HTTP port (8080) and TCP port (9090)
- [ ] `application.yml` values overridable via environment variables (`SPRING_DATASOURCE_URL`, etc.)
- [ ] Health check: `HEALTHCHECK CMD curl -f http://localhost:8080/actuator/health || exit 1`
- [ ] `.dockerignore` excludes `.git`, `node_modules`, `build/`, IDE files
- [ ] `docker build -t orchestration-manager .` succeeds
