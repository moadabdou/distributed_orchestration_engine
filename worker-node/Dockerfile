# ──────────────────────────────────────────────────────────────────
# Stage 1 — Builder (compile & package the fat JAR)
# ──────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# ── Layer 1: Parent POM (changes rarely) ──
COPY pom.xml .

# ── Layer 2: Module POMs only (changes when dependencies change) ──
COPY engine-core/pom.xml engine-core/
COPY worker-node/pom.xml worker-node/
COPY manager-node/pom.xml manager-node/

# ── Layer 3: Resolve & download all dependencies ──
RUN mvn -pl engine-core,worker-node -am dependency:go-offline \
    --batch-mode -Dmaven.test.skip=true

# ── Layer 4: Copy source code (changes frequently) ──
COPY engine-core/src engine-core/src
COPY worker-node/src worker-node/src

# ── Layer 5: Compile & package (uses pre-downloaded deps) ──
RUN mvn -pl engine-core,worker-node -am package -Dmaven.test.skip=true \
    --batch-mode -q

# ──────────────────────────────────────────────────────────────────
# Stage 2 — Runtime (slim JRE image)
# ──────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install Python 3, Bash, and required libraries
RUN apk add --no-cache python3 py3-pip bash \
    && pip install --no-cache-dir --break-system-packages \
    requests \
    sqlalchemy \
    psycopg2-binary \
    docker \
    kubernetes \
    papermill \
    pandas

# Copy the fat JAR from the builder stage (explicit filename to avoid COPY glob ambiguity)
COPY --from=builder /build/worker-node/target/worker-node-1.0.0-SNAPSHOT.jar app.jar

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Expose nothing — workers initiate outbound TCP to manager only
# (no inbound ports to expose)

# Environment variables (configurable at runtime):
#   MANAGER_HOST              — manager hostname or Docker service name (default: localhost)
#   MANAGER_PORT              — manager TCP port                       (default: 9090)
#   WORKER_AUTH_TOKEN         — JWT authentication token               (required, or use WORKER_DEV_MODE)
#   WORKER_DEV_MODE           — set "true" to auto-generate JWT        (default: false)
#   MANAGER_SECURITY_JWT_SECRET — JWT secret for dev mode              (uses hardcoded default)
#
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
