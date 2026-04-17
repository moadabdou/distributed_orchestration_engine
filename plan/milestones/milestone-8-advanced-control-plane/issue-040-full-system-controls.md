# Issue 040: Full System Controls

## Description
Provide granular write-access controls in the dashboard to manage worker nodes interactively, including draining nodes, forcing disconnects/reconnections, and updating worker tags.

**Note:** This issue is independent of the DAG Visualizer (Issue 038 series) and can be implemented in parallel or after Phase 4. It focuses on **worker node management**.

## Requirements
- Add worker management UI with action buttons (Drain, Disconnect, Reconnect)
- Implement backend endpoints for worker lifecycle operations
- Support updating worker tags/labels directly from the UI
- Require confirmation for destructive operations (drain, disconnect)
- Reflect worker state changes in real-time across the dashboard
- All operations must be logged and auditable

## Acceptance Criteria
- [ ] Users can drain a worker node (complete current jobs, stop accepting new ones)
- [ ] Users can force disconnect a worker from the manager
- [ ] Users can trigger reconnection attempts for disconnected workers
- [ ] Worker tags can be edited via an inline form or modal
- [ ] All operations are logged and auditable
- [ ] Real-time updates reflect worker state changes in the dashboard

## Dependencies
- Milestone 3 worker registry
- Milestone 5 async worker client
- Issue 038.8 (Workflow REST API) — for consistent API patterns and error handling

## Implementation Order
This issue can be implemented **in parallel with Issue 038 series** or **after Issue 038.14 (Phase 4 Frontend)** because:
1. It uses the same dashboard infrastructure and API patterns
2. Worker controls are orthogonal to workflow/DAG features
3. Backend worker registry must be stable before adding write operations

## Deliverables
```
manager-node/
  src/main/java/com/doe/manager/api/controller/
    WorkerControlController.java      -- NEW (drain, disconnect, reconnect, update-tags)

dashboard/
  src/components/
    WorkerControlPanel.tsx            -- NEW
    WorkerActionButtons.tsx           -- NEW (drain, disconnect, reconnect)
    WorkerTagEditor.tsx               -- NEW
  src/api/
    workerControls.ts                 -- NEW (API functions for worker operations)
  src/pages/
    WorkersView.tsx                   -- NEW (dedicated worker management page)
```

## Notes
- Drain operation: mark worker as DRAINING, let current jobs complete, stop accepting new jobs
- Force disconnect: terminate connection immediately, reassign pending jobs if possible
- Reconnect: trigger reconnection attempt for disconnected workers
- Tag editing: validate tag format, prevent duplicates, handle concurrent edits
- Audit logging: log all worker control operations with timestamp, user, and outcome
