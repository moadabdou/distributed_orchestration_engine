# Issue 038.11: DAG Preview Panel & Workflows View Page

## Phase
**Phase 4: Frontend — DAG Visualizer & Graphical Control**

## Description
Implement the DAG preview panel on the dashboard homepage and a dedicated workflows view page for listing and managing workflows.

## Scope

### 1. Dashboard Layout Changes

**Current layout:**
```
┌─────────────────────────────────────┐
│  Metric Cards (8 cards, 1 row)      │
├──────────────────┬──────────────────┤
│  Worker Nodes    │  Activity Feed   │
│  (left half)     │  (right half)    │
└──────────────────┴──────────────────┘
```

**New layout:**
```
┌─────────────────────────────────────┐
│  Metric Cards (8 cards, 1 row)      │
├─────────────────────────────────────┤
│  Workflow DAG Preview Panel         │  ← NEW: full-width, collapsible
│  [mini DAG graph] [✏️ Edit] [➕ New] │
├──────────────────┬──────────────────┤
│  Worker Nodes    │  Activity Feed   │
│  (left half)     │  (right half)    │
└──────────────────┴──────────────────┘
```

### 2. DagPreviewPanel Component
- Full-width panel below metric cards
- Shows the **most recent active workflow** (RUNNING > PAUSED > DRAFT > COMPLETED)
- Read-only React Flow view with status-colored nodes
- Toolbar: workflow name, status badge, edit button (pencil), new workflow button (plus)
- If no workflows exist: shows "No workflows yet — create one" message with a CTA button
- Polling: `refetchInterval: 3000ms` (consistent with existing dashboard polling)
- Workflow selector: dropdown to switch between workflows if multiple exist

### 3. WorkflowsView Page
New page at `/workflows` route:

- Table/list view with status, job count, created date
- Create new workflow button
- Actions per row: view DAG, execute, pause, resume, reset, delete
- Status filter and search by name
- Pagination support

### 4. Route Updates
`App.tsx` — add new route:
```typescript
<Route path="/workflows" element={<WorkflowsView />} />
```

`DashboardHome.tsx` — insert `DagPreviewPanel`:
```typescript
import DagPreviewPanel from '../components/DagPreviewPanel';

// In the layout, after metric cards:
<DagPreviewPanel />
```

### 5. Workflow Actions Toolbar
- **Execute** button (when DRAFT or PAUSED)
- **Pause** button (when RUNNING)
- **Resume** button (when PAUSED)
- **Reset** button (when not RUNNING — resets to DRAFT)
- **Delete** button (when not RUNNING)
- Each action shows confirmation dialog
- Actions use `useWorkflowActions` hook for mutations

## Acceptance Criteria
- [ ] `DagPreviewPanel` renders the most recent active workflow
- [ ] Panel shows empty state with CTA when no workflows exist
- [ ] Workflow selector dropdown works for multiple workflows
- [ ] `WorkflowsView` lists workflows with filtering, sorting, and pagination
- [ ] Workflow actions (execute, pause, resume, reset, delete) work with confirmation dialogs
- [ ] Real-time updates via polling (3s interval)
- [ ] Route `/workflows` added and accessible
- [ ] `DagPreviewPanel.test.tsx` and `WorkflowsView.test.tsx` pass

## Deliverables
```
dashboard/
  src/components/
    DagPreviewPanel.tsx                 -- NEW
    DagToolbar.tsx                      -- NEW
    WorkflowSelector.tsx                -- NEW

  src/pages/
    WorkflowsView.tsx                   -- NEW

  src/pages/
    DashboardHome.tsx                   -- UPDATED (insert DagPreviewPanel)

  src/App.tsx                           -- UPDATED (add /workflows route)

  src/test/
    components/DagPreviewPanel.test.tsx
    pages/WorkflowsView.test.tsx
```

## Dependencies
- Issue 038.10 (Frontend Foundation — Types, API Functions & Utilities)
- Issue 038.8 (Workflow REST API — DTOs & Controller) — backend must be ready

## Notes
- Polling overhead: at 3s intervals, polling the DAG endpoint for large workflows (100+ nodes) could be heavy.
- Mobile responsiveness: consider a full-screen overlay on mobile breakpoints for the preview panel.
