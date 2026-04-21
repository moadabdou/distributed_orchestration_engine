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
# Stage 2 — Runtime (Debian-based JRE image for better compatibility with ML libs)
# ──────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install Python 3, Bash, curl and required libraries
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3 \
    python3-pip \
    python3-dev \
    build-essential \
    bash \
    curl \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Upgrade pip first to avoid issues with older versions
RUN pip3 install --no-cache-dir --upgrade pip setuptools wheel

# Install utility and database libraries separately
RUN pip3 install --no-cache-dir \
    requests \
    sqlalchemy \
    psycopg2-binary \
    docker \
    kubernetes \
    papermill \
    pandas \
    minio \
    numpy

# Install ML libraries
# We use the CPU version of torch to keep the image size manageable
RUN pip3 install --no-cache-dir \
    torch --index-url https://download.pytorch.org/whl/cpu

# Install high-level ML libs (depends on torch/numpy)
RUN pip3 install --no-cache-dir \
    scikit-learn \
    transformers \
    sentence-transformers

# Pre-download the embedding model to avoid runtime downloads during tests
RUN python3 -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')"

# ─── Install Fern-OS Python SDK ─────────────────────────────
COPY python-sdk /tmp/python-sdk
RUN pip3 install --no-cache-dir /tmp/python-sdk && rm -rf /tmp/python-sdk

# Copy the fat JAR from the builder stage (explicit filename to avoid COPY glob ambiguity)
COPY --from=builder /build/worker-node/target/worker-node-1.0.0-SNAPSHOT.jar app.jar

# Create a non-root user for security
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
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
