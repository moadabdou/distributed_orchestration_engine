# Issue #056 — Manager-side Event Registration & Routing

## Description
Implement workflow-scoped event routing and strict ownership logic in `ManagerServer` using session-level identity association.

## Requirements
- Maintain event ownership registry: `Map<WorkflowId, Map<EventName, OwnerJobId>>`.
- Maintain subscription registry: `Map<WorkflowId, Map<EventName, Set<Connection>>>`.
- **Handle `REGISTER_JOB_EVENTS`**:
    - Validate JWT and associate the `workflowId` and `jobId` with the `EventsConnection`.
- **Handle `EVENT_PUBLISH`**:
    - Use the connection's pre-verified `jobId` to check ownership of the `eventName`.
    - This model relies on secure transport (SSH/TLS) for MITM protection and prioritizes signaling performance.
- Route `EVENT_NOTIFY` only to subscribers within the same `workflowId`.

## Acceptance Criteria
- Only the pre-verified owner of an event can emit it.
- Signals are routed with minimal CPU overhead.
