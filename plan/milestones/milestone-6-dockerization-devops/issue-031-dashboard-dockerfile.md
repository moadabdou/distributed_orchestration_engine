#035 — Dockerfile for TypeScript Dashboard (Nginx)

**Milestone:** 5 — Dockerization & DevOps  
**Labels:** `devops`, `docker`, `frontend`, `priority:high`  
**Assignee:** —  
**Estimate:** 0.5 day  
**Depends on:** #020  

## Description

Create a multi-stage Dockerfile that builds the React app and serves static files via Nginx, with API reverse proxy to the Manager.

### Dockerfile Structure

```dockerfile
# Stage 1: Build
FROM node:18-alpine AS builder
WORKDIR /app
COPY dashboard/package*.json ./
RUN npm ci
COPY dashboard/ .
RUN npm run build

# Stage 2: Serve
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### Nginx Config

```nginx
server {
    listen 80;
    
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;  # SPA fallback
    }
    
    location /api/ {
        proxy_pass http://manager:8080;  # Docker DNS
    }
}
```

## Acceptance Criteria

- [ ] Multi-stage: Node for build, Nginx Alpine for serve
- [ ] Final image size < 50 MB
- [ ] SPA routing works (all paths serve `index.html`)
- [ ] `/api/*` reverse-proxied to `manager:8080` via Nginx
- [ ] `docker build -t orchestration-dashboard .` succeeds
- [ ] Dashboard loads at `http://localhost:80` inside Docker network
