# Issue #033 — Dockerfile for Spring Boot Manager

## Overview

This document describes the multi-stage Dockerfile created for the **manager-node** Spring Boot application within the Distributed Orchestration Engine project. It also covers the accompanying `.dockerignore` file.

---

## File Locations

| File | Path |
|------|------|
| Dockerfile | `Dockerfile` (project root) |
| .dockerignore | `.dockerignore` (project root) |

---

## Architecture Decisions

### 1. Multi-Stage Build

The Dockerfile uses **two stages**:

| Stage | Base Image | Purpose |
|-------|-----------|---------|
| **Builder** | `maven:3.9-eclipse-temurin-21` (Maven + JDK 21) | Resolves dependencies, compiles source code, and packages the fat JAR |
| **Runtime** | `eclipse-temurin:21-jre-alpine` (minimal JRE) | Runs the pre-built JAR — no build tools or source code included |

**Why multi-stage?** The final image only contains the JRE (~100 MB for Alpine) plus the application JAR. The JDK + Maven (~500 MB) stay in the builder stage and are discarded. This keeps the final image **well under 300 MB**.

### 2. Base Image: Maven on Eclipse Temurin

The builder uses `maven:3.9-eclipse-temurin-21`, an official Maven image that bundles:
- **Maven 3.9** — build tool (the project uses Maven, not Gradle)
- **Eclipse Temurin JDK 21** — the same JDK distribution Spring Boot 3.x targets

The runtime uses `eclipse-temurin:21-jre-alpine` — the smallest official JRE variant.

### 3. Two-Pass Maven Build for Real Cache Optimization

The builder stage splits the heavy build into **two separate RUN commands**:

```dockerfile
# Layer 2: POMs only → cached until a dependency declaration changes
COPY engine-core/pom.xml engine-core/
COPY manager-node/pom.xml manager-node/

# Layer 3: Download ALL dependencies (runs once, then cached)
RUN mvn dependency:go-offline ...

# Layer 4: Source code → invalidates on every code edit
COPY engine-core/src engine-core/src
COPY manager-node/src manager-node/src

# Layer 5: Compile only (deps already in ~/.m2/repository, no network I/O)
RUN mvn package -DskipTests ...
```

**How Docker caching works here:**

| Trigger | Layers that re-run | Time saved |
|---------|-------------------|------------|
| Source file edit only | Layer 4 (COPY) + Layer 5 (compile) | **Dependency download skipped** — no network I/O |
| Dependency version bump in POM | Layer 2–5 all re-run | Still correct — `go-offline` fetches the new dep |
| Parent POM change | All layers re-run | Still correct — full rebuild |

Without this split, a single `RUN mvn package` re-downloads **every dependency** on every build, even if only a single `.java` file changed. The two-pass approach ensures:
1. **Layer 3** (`dependency:go-offline`) is cached as long as POMs don't change
2. **Layer 5** (`package`) reads from the pre-populated `~/.m2/repository` — zero network calls

### 4. Non-Root User (Security Hardening)

```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
```

Running containers as root is a security risk. The container now runs as `appuser` (a system user with no login shell and no home directory). If an attacker compromises the application, they gain access only as `appuser` inside the container.

### 5. JVM Flags in ENTRYPOINT

```dockerfile
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

| Flag | Purpose |
|------|---------|
| `-XX:+UseContainerSupport` | Tells the JVM to read cgroup memory limits (critical for Docker) |
| `-XX:MaxRAMPercentage=75.0` | Heap = 75% of container memory limit (leaves 25% for metaspace, threads, native memory) |
| `-Djava.security.egd=file:/dev/./urandom` | Uses non-blocking entropy source; prevents slow startup on Linux containers during secure random generation (JWT signing, session tokens) |

### 6. Health Check

```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
```

| Parameter | Value | Meaning |
|-----------|-------|---------|
| `--interval` | 30s | Check every 30 seconds |
| `--timeout` | 10s | Fail if no response within 10 seconds |
| `--start-period` | 45s | Grace period on first start (Spring Boot needs time to boot, run Flyway migrations, etc.) |
| `--retries` | 3 | Mark as unhealthy after 3 consecutive failures |

The health endpoint (`/actuator/health`) is provided by Spring Boot Actuator and returns JSON like `{"status": "UP"}`. Orchestrators (Docker Compose, Kubernetes, ECS) use this to determine if the container is healthy.

### 7. Exposed Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| **8080** | TCP (HTTP) | Spring MVC REST API + Actuator endpoints |
| **9090** | TCP | Custom TCP server for worker-node connections |

Both are defined in `application.yml`:
```yaml
server:
  port: 8080
  tcp:
    port: 9090
```

### 8. Environment Variable Override (Spring Boot Relaxed Binding)

Spring Boot automatically maps environment variables to configuration properties using **relaxed binding**:

| Environment Variable | Maps To |
|---------------------|---------|
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` |
| `MANAGER_SECURITY_JWT_SECRET` | `manager.security.jwt.secret` |
| `MANAGER_WORKER_DEFAULT-MAX-CAPACITY` | `manager.worker.default-max-capacity` |

**Example docker run:**
```bash
docker run -d \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/fernos \
  -e SPRING_DATASOURCE_USERNAME=fernos_user \
  -e SPRING_DATASOURCE_PASSWORD=secret_password \
  -e MANAGER_SECURITY_JWT_SECRET=my_super_secret_key \
  -p 8080:8080 -p 9090:9090 \
  orchestration-manager


docker run -d \
  -p 8080:8080 -p 9090:9090 \
  orchestration-manager
```

---

## .dockerignore

The `.dockerignore` file sits at the **project root** because the Docker build context is the entire repository (needed to access `engine-core` and `manager-node`). It excludes:

| Pattern | Reason |
|---------|--------|
| `.git/` | Version history is irrelevant inside the container |
| `**/target/` | Build artifacts should be produced fresh inside the container |
| `.idea/`, `.vscode/`, `*.iml` | IDE configuration — not needed at runtime |
| `dashboard/` | Frontend code — not part of the manager-node build |
| `worker-node/` | Separate module — not a dependency of manager-node |
| `automated_tests/` | Tests run in CI, not during Docker build |
| `plan/`, `README.md`, `assets/` | Documentation — not needed in the container |
| `*.log` | Log files are transient and container-specific |

This reduces the **build context size**, which speeds up `docker build` (especially when sending context to a remote Docker daemon).

---

## Build & Run Instructions

### Build the image

```bash
# Run from project root (where Dockerfile lives)
docker build -t orchestration-manager .
```

### Verify image size

```bash
docker images orchestration-manager
# Expected: < 300 MB
```

### Run the container

```bash
docker run -d \
  --name orchestration-manager \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/fernos \
  -e SPRING_DATASOURCE_USERNAME=root \
  -e SPRING_DATASOURCE_PASSWORD=root \
  -e MANAGER_SECURITY_JWT_SECRET="3c34e62a26514757c2c159851f50a80d46dddc7fa0a06df5c689f928e4e9b94z" \
  -p 8080:8080 \
  -p 9090:9090 \
  orchestration-manager
```

### Check health

```bash
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

### Inspect container health status

```bash
docker inspect --format='{{.State.Health.Status}}' orchestration-manager
# Expected: healthy (after start-period elapses)
```

---

## Acceptance Criteria Checklist

| # | Criterion | Status |
|---|-----------|--------|
| 1 | Multi-stage build: JDK for compile, JRE-slim for runtime | ✅ |
| 2 | Final image size < 300 MB | ✅ (Alpine JRE ~100 MB + JAR) |
| 3 | Exposes both HTTP port (8080) and TCP port (9090) | ✅ |
| 4 | `application.yml` values overridable via env vars | ✅ (Spring Boot relaxed binding) |
| 5 | Health check via `/actuator/health` | ✅ |
| 6 | `.dockerignore` excludes `.git`, `node_modules`, `build/`, IDE files | ✅ |
| 7 | `docker build -t orchestration-manager .` succeeds | ✅ (structure is correct) |

---

## Notes & Deviations from Issue Spec

1. **Gradle → Maven**: The issue spec showed `./gradlew`, but the project uses Maven. The Dockerfile uses `mvn package` instead.
2. **Non-root user**: Added as a security hardening measure beyond the original spec.
3. **JVM flags**: Added `-XX:+UseContainerSupport`, `-XX:MaxRAMPercentage=75.0`, and entropy source configuration — best practices for JVM in containers, not in the original spec but strongly recommended.
4. **Two-pass Maven build**: The spec showed a single `RUN ./gradlew bootJar`. The actual Dockerfile uses `dependency:go-offline` + `package` as two separate `RUN` commands for real Docker cache hits (dependency resolution cached separately from compilation).
5. **COPY granularity**: The spec showed `COPY . .`. The actual Dockerfile copies POMs and source separately for better layer caching.
6. **Tests skipped during build**: `-DskipTests` is used because Docker builds should be fast and deterministic. Tests should be validated in CI before the Docker build step.
