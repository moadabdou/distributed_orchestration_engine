# Issue 038.1: Workflow Domain Models & DAG Validator

## Phase
**Phase 1: Engine — In-Memory Workflow Management**

## Description
Create the core domain models for workflow management and implement DAG validation utilities including cycle detection.

## Scope

### 1. Domain Models (engine-core)
- `Workflow.java` — workflow entity with ID, name, status, jobs, dependencies
- `WorkflowStatus.java` — enum: DRAFT, RUNNING, PAUSED, COMPLETED, FAILED
- `WorkflowJob.java` — wraps existing Job with DAG index and dependency list

### 2. DAG Validator (engine-core)
- `DagValidator.java` — validates DAG structure
  - Cycle detection (DFS-based)
  - Self-dependency check
  - Missing dependency check
- `DagValidationError.java` — error model with descriptive messages

## Acceptance Criteria
- [ ] `Workflow`, `WorkflowJob`, `WorkflowStatus` models defined
- [ ] `DagValidator` detects cycles in linear, diamond, and complex graphs
- [ ] `DagValidator` catches self-dependencies and missing dependencies
- [ ] `DagValidatorTest` covers all validation scenarios
- [ ] Models are thread-safe ready (immutable where possible)

## Deliverables
```
engine-core/
  src/main/java/com/doe/core/model/
    Workflow.java
    WorkflowStatus.java
    WorkflowJob.java
  src/main/java/com/doe/core/util/
    DagValidator.java
    DagValidationError.java
  src/test/java/com/doe/core/util/
    DagValidatorTest.java
```

## Dependencies
- Milestone 1-7 engine-core foundations
- Existing `Job` model to wrap in `WorkflowJob`

## Notes
- Keep models immutable where possible
- No persistence yet — pure in-memory domain models
- Thread safety will be handled in WorkflowManager (next issue)
