# ──────────────────────────────────────────────────────────────────
# Stage 1 — Build (compile the React app)
# ──────────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

# ── Layer 1: Dependencies ──
COPY dashboard/package*.json ./
RUN npm ci --ignore-scripts --prefer-offline --no-audit

# ── Layer 2: Source code ──
COPY dashboard/ .

# ── Layer 3: Build ──
RUN npm run build

# ──────────────────────────────────────────────────────────────────
# Stage 2 — Serve (Nginx Alpine)
# ──────────────────────────────────────────────────────────────────
FROM nginx:alpine

# Remove default Nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy built assets and custom Nginx config
COPY --from=builder /app/dist /usr/share/nginx/html
COPY dashboard/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
