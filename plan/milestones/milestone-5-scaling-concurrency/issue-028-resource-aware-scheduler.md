#032 — Resource-Aware Scheduler

**Milestone:** 5 — Scaling & Concurrency  
**Labels:** `manager-node`, `scheduling`, `priority:medium`

## Description

With `WorkerConnection` structurally modeling concurrent assignments, our `JobScheduler` must dynamically query availability not by hunting for a purely `IDLE` flip-switch, but by locating nodes harboring residual headroom.

## Acceptance Criteria

- [ ] Update the `JobScheduler` loop to locate workers utilizing `findWorkerWithAvailableCapacity()`.
- [ ] Ensure thread-safe loop operations; workers are skipped if actively saturating their maximum theoretical boundary.
- [ ] Reconfigure the REST metrics dashboards to accurately chart utilization rates (`activeJobs / maxCapacity` across the entire registry array).
- [ ] Construct integration load tests routing 10 simultaneous rapid-fire jobs directly at two active quad-capacity workers to verify complete parallel occupation without artificial drops in throughput latency.
- [ ] Remove hardcoded ANSI color codes (`\u001B[31m`) in `WorkerConnection` logging to prevent log pollution in production systems.
- [ ] Externalize the `maxCapacity` limit (currently hardcoded to 4) to an `application.yml` property for dynamic scaling configuration.
