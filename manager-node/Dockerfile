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
#    Cached unless any POM layer above changes.
#    Subsequent builds skip network I/O entirely.
RUN mvn -pl engine-core,manager-node -am dependency:go-offline \
    --batch-mode -Dmaven.test.skip=true

# ── Layer 4: Copy source code (changes frequently) ──
COPY engine-core/src engine-core/src
COPY manager-node/src manager-node/src

# ── Layer 5: Compile & package (uses pre-downloaded deps) ──
#    Only recompiles changed source; dependency resolution is instant
#    from the local Maven cache populated in Layer 3.
RUN mvn -pl engine-core,manager-node -am package -Dmaven.test.skip=true \
    --batch-mode -q

# ──────────────────────────────────────────────────────────────────
# Stage 2 — Runtime (slim JRE image)
# ──────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Install curl (needed by HEALTHCHECK) and clean up in a single layer
RUN apk add --no-cache curl

WORKDIR /app

# Copy the fat JAR from the builder stage
COPY --from=builder /build/manager-node/target/*.jar app.jar

# Pre-create log storage directory and set permissions
RUN mkdir -p data/var/logs/jobs && \
    addgroup -S appgroup && adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app/data
USER appuser

# Expose HTTP (Spring MVC + Actuator) and TCP (worker connections)
EXPOSE 8080 9090

# Health check against Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# All application.yml values are overridable via environment variables.
# Spring Boot automatically maps env vars like SPRING_DATASOURCE_URL to
# spring.datasource.url (relaxed binding).
#
# Required env vars at runtime:
#   SPRING_DATASOURCE_URL     — jdbc:postgresql://<db-host>:5432/fernos
#   SPRING_DATASOURCE_USERNAME
#   SPRING_DATASOURCE_PASSWORD
#   MANAGER_SECURITY_JWT_SECRET
#
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
