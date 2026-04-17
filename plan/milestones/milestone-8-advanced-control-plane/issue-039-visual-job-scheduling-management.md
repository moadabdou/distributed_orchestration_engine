# Issue 039: Visual Job Scheduling & Management

## Description
Enable interactive job lifecycle management from the dashboard, allowing users to schedule, cancel, cancel all remaining, or retry jobs directly from the UI.

**Note:** This issue is now decoupled from the DAG Visualizer (Issue 038 series) and focuses on **individual job controls** that work for both standalone jobs and jobs within workflows.

## Requirements
- Add action buttons (Cancel, Cancel All Remaining, Retry) to job detail views
- Implement backend API endpoints for each action with proper validation
- **Retry Logic & Constraints:**
  - Only permit retries if the job is in a termination state.
  - A job must have an `idempotent` flag set to true to be retriable; otherwise, the retry action should be disabled/rejected.
  - The job's dependencies must all be in a complete state. If not, the API should return a specific reason (e.g., "Dependencies not satisfied") and the UI must display this in a toast notification.
  - The parent workflow must be in a completely stalled/terminal state (no jobs currently running). If the workflow or any of its jobs are still running, retry is not possible.
- **Retry Cascading Effects:**
  - Retrying a job must set all downstream jobs depending on it back to `pending`.
  - These dependent jobs must be removed from the workflow's tracking map of submitted jobs.
  - The retry action should automatically resume the workflow execution so the scheduler can evaluate and pick up the retried job on the next tick.
- **Cancel All:**
  - Add an option to cancel all pending/remaining jobs that are not in a completed state.
- Display confirmation dialogs for destructive actions (Cancel, Cancel All, Retry)
- Show real-time feedback as jobs transition states
- Integrate with existing `/jobs` endpoints (backward compatible)

## Acceptance Criteria
- [ ] Users can cancel individual pending or running jobs
- [ ] Users can trigger a "Cancel All" action that cancels all jobs not yet in a completed state
- [ ] Users can retry a job only if it is in a termination state and explicitly marked as `idempotent`
- [ ] Retrying a job is blocked with an API-provided toast reason if its dependencies are not complete
- [ ] Retrying a job is blocked if its parent workflow still has running jobs
- [ ] Retrying a job automatically cascades: resets dependent jobs to `pending`, clears them from tracking tracking map of submitted jobs, and resumes workflow execution
- [ ] UI provides clear success/error feedback for each action
- [ ] Actions update the parent workflow state correctly (if job belongs to a workflow)

## Dependencies
- Milestone 2 job state machine
- Milestone 3 REST API controllers
- Issue 038.8 (Workflow REST API) — for workflow state sync when jobs are part of workflows

## Implementation Order
This issue should be implemented **after Issue 038.9 (Phase 3 API)** because:
1. Workflow engine must exist to handle job state changes within workflows
2. Job actions must trigger workflow state re-evaluation (via `JobResultListener`)
3. Dashboard already has polling infrastructure from Phase 4 setup

## Deliverables
```
manager-node/
  src/main/java/com/doe/manager/api/controller/
    JobActionController.java          -- NEW (cancel, cancel-all, retry endpoints)

dashboard/
  src/components/
    JobActionButtons.tsx              -- NEW (cancel, cancel-all, retry buttons)
  src/api/
    jobActions.ts                     -- NEW (API functions for job actions)
```

## Notes
- Job actions on standalone jobs (auto-workflows) work the same as workflow jobs
- When a job action affects workflow state (e.g., job failure → workflow failure), the `JobResultListener` handles the cascade
- Jobs should have an explicit `idempotent` flag in the data model to dictate retry eligibility.
