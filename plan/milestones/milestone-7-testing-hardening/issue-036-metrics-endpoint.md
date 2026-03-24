#040 — Metrics Endpoint (Actuator + Micrometer)

**Milestone:** 6 — Testing & Hardening  
**Labels:** `observability`, `metrics`, `spring-boot`, `priority:medium`  
**Assignee:** —  
**Estimate:** 0.5 day  
**Depends on:** #011  

## Description

Expose operational metrics via Spring Boot Actuator so the system can be monitored with Prometheus/Grafana in the future.

### Custom Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `orchestration.jobs.submitted` | Counter | Total jobs submitted via API |
| `orchestration.jobs.completed` | Counter | Total jobs completed |
| `orchestration.jobs.failed` | Counter | Total jobs failed |
| `orchestration.jobs.queue.size` | Gauge | Current pending queue depth |
| `orchestration.workers.active` | Gauge | Currently connected workers |
| `orchestration.jobs.duration` | Timer | Job execution duration distribution |

## Acceptance Criteria

- [ ] `spring-boot-starter-actuator` dependency added
- [ ] `/actuator/health` returns UP/DOWN status
- [ ] `/actuator/metrics` lists all custom metrics
- [ ] `/actuator/metrics/orchestration.jobs.submitted` returns current count
- [ ] Metrics updated in real-time as jobs flow through the system
- [ ] Prometheus format available at `/actuator/prometheus` (add `micrometer-registry-prometheus`)
- [ ] Actuator endpoints secured: only `health`, `metrics`, `prometheus` exposed
