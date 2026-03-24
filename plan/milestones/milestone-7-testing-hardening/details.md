# Milestone 7: Testing, Chaos Engineering & Production Hardening

## Overview

Validate the entire system under stress and failure conditions. This milestone adds structured logging, builds a chaos testing harness, and ensures no jobs are ever lost under adversarial conditions.

This is what separates a demo project from a **production-grade** system.

## Goals

1. **Structured logging** — SLF4J + Logback with MDC context (Job ID, Worker ID) in every log line.
2. **Integration test suite** — End-to-end tests that spin up Manager + Workers programmatically.
3. **Chaos testing harness** — Automated script that submits bulk jobs and randomly kills workers mid-execution.
4. **Metrics endpoint** — `/actuator/metrics` exposing jobs processed, active workers, queue depth.
5. **Documentation** — README with architecture diagram, quickstart guide, and contribution guidelines.

## Architecture Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Logging framework | SLF4J + Logback | Spring Boot default, MDC for correlation |
| Log format | JSON structured logs | Machine-parseable, Elasticsearch/Kibana-ready |
| Chaos tool | Custom bash + Java script | Lightweight, no external dependencies |
| Metrics | Spring Boot Actuator + Micrometer | Industry standard, Prometheus-compatible |

## Success Criteria

- [ ] Submit 10,000 jobs with 5 workers → all 10,000 reach `COMPLETED` or `FAILED` (zero lost)
- [ ] Kill 2 out of 5 workers mid-execution → recovery < 30 seconds, no lost jobs
- [ ] Kill Manager mid-execution → restart → orphaned jobs recovered and completed
- [ ] Every log line includes correlation IDs (job UUID, worker UUID)
- [ ] `/actuator/metrics` returns live counters
- [ ] README with full architecture diagram, quickstart, API reference

## Dependencies

- Milestones 1–5 complete

## Estimated Effort

**4–5 working days**
