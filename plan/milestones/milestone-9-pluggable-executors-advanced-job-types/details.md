# Milestone 9: Pluggable Executors & Advanced Job Types

## Overview

Transform the engine from a "shell command runner" into a **versatile data & infrastructure orchestration platform** by introducing a pluggable executor architecture and domain-specific operators (Python, HTTP, SQL, Docker/K8s, Sensors).

This milestone mirrors the capabilities of modern orchestration engines like Airflow, Prefect, and Dagster.

## Goals

1. **Pluggable Task Executor Interface (SPI)** — Standardized interface allowing custom executors without modifying core engine code.
2. **Advanced Job Types / Operators:**
   - Python Operator (scripts, functions, notebooks with virtualenv/conda isolation)
   - HTTP/API Operator (request execution, retries, response validation)
   - SQL/Database Operator (query execution across PostgreSQL, MySQL, BigQuery)
   - Docker/Kubernetes Operator (container lifecycle management, K8s deployment)
   - Sensor/Trigger Operator (wait for external events: file arrival, API response, time-based triggers)
3. **Environment & Dependency Management** — Per-job dependency injection, custom Docker images, or virtual environments.
4. **Result Passing & XComs** — Mechanism for jobs to exchange data between DAG nodes, enabling dynamic workflows.
5. **Retries & Alerting per Operator Type** — Configurable retry policies specific to each operator.


## Architecture Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Executor Plugin System | Java SPI (ServiceLoader) | Native Java plugin mechanism, no external dependencies |
| Python Execution | Subprocess + virtualenv | Lightweight, no need for embedded Python runtime |
| SQL Execution | JDBC drivers (pluggable) | Universal database connectivity |
| Docker/K8s Execution | Docker Java API + Fabric8 K8s client | Industry-standard Java clients |
| XCom Storage | Database-backed (PostgreSQL) | Persistent, queryable, survives restarts |

## Success Criteria

- [ ] New executor type can be added by implementing a single interface and registering via SPI
- [ ] Python jobs execute with isolated virtualenv/conda support
- [ ] HTTP jobs support GET/POST/PUT/DELETE with configurable retries and response validation
- [ ] SQL jobs execute queries against multiple database types and return results
- [ ] Docker jobs run containers, capture logs, and manage lifecycle
- [ ] Sensor jobs wait for external events and trigger downstream jobs
- [ ] XCom mechanism allows jobs to pass data between DAG nodes
- [ ] Each operator type has configurable retry policies

## Dependencies

- Milestones 1–8 complete
- Pluggable task executor foundation from Milestone 3 (issue-019)
- Control plane from Milestone 8 for operator configuration

## Estimated Effort

**6–8 working days**
