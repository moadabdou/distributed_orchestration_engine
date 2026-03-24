# Issue #019 — Pluggable Task Executor Architecture

**Milestone:** 3 — Spring Boot Management Plane  
**Labels:** `worker-node`, `engine-core`, `architecture`, `priority:high`  
**Assignee:** —  
**Estimate:** 1.5 days  
**Depends on:** #008  

## Description

Replace the hardcoded `DummyTaskExecutor` with a pluggable architecture capable of dynamically loading and executing tasks based on payload context (e.g., executing bash scripts, calling external APIs, or executing JARs).

## Acceptance Criteria

- [ ] Define a `TaskPlugin` abstraction.
- [ ] Mechanism in `WorkerClient` to detect task type from job payload and select the appropriate plugin.
- [ ] Provide at least one realistic plugin (e.g. a `ProcessBuilder` or `ScriptExecutor` plugin).
- [ ] Ensure proper output capturing and resource cleanup around plugin execution.
