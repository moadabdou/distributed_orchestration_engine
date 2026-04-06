# Issue #011 — Refactor Manager to Spring Boot Service

**Milestone:** 3 — Spring Boot Management Plane  
**Labels:** `manager-node`, `spring-boot`, `refactor`, `priority:high`  
**Assignee:** —  
**Estimate:** 2 day  
**Depends on:** #003  

## Description

Convert the standalone `ManagerServer` into a Spring Boot-managed `@Service` that starts alongside the application context.

## Acceptance Criteria

- [ ] `manager-node` module adds Spring Boot Starter dependencies (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`)
- [ ] `ManagerService` annotated with `@Service`, implementing `SmartLifecycle` or using `@PostConstruct` / `@PreDestroy`
- [ ] TCP server starts automatically on Spring Boot startup (`server.tcp.port` configurable in `application.yml`)
- [ ] HTTP port and TCP port are independent (e.g., HTTP on `8080`, TCP on `9090`)
- [ ] `WorkerRegistry`, `JobQueue`, and `JobScheduler` injected as Spring beans (`@Component`)
- [ ] Application starts cleanly with `./gradlew bootRun` and logs both HTTP + TCP readiness
- [ ] Existing Worker clients connect without changes
