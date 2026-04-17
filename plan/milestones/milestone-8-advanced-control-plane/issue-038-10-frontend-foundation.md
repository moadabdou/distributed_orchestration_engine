# Issue 038.10: Frontend Foundation — Types, API Functions & Utilities

## Phase
**Phase 4: Frontend — DAG Visualizer & Graphical Control**

## Description
Set up the frontend foundation for workflow visualization: install dependencies, add TypeScript types, create API functions, and implement utility functions for DAG layout and cycle detection.

## Scope

### 1. Dependencies
```bash
npm install @xyflow/react @dagrejs/dagre
```

- **`@xyflow/react`** (v12+) — React Flow library for node-edge visualization
- **`@dagrejs/dagre`** — Auto-layout engine (topological layering)

### 2. TypeScript Types
Add to `dashboard/src/types/api.ts`:

```typescript
interface Workflow {
  id: string;
  name: string;
  status: 'DRAFT' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED';
  totalJobs: number;
  completedJobs: number;
  failedJobs: number;
  pendingJobs: number;
  createdAt: string;
  updatedAt: string;
}

interface WorkflowSummary {
  id: string;
  name: string;
  status: Workflow['status'];
  totalJobs: number;
  createdAt: string;
}

interface DagNode {
  jobId: string;
  label: string;
  dagIndex: number;
  status: Job['status'];
  payload: string;
  result: string | null;
  workerId: string | null;
  createdAt: string;
  updatedAt: string;
  position?: { x: number; y: number };
}

interface DagEdge {
  sourceJobId: string;
  targetJobId: string;
}

interface DagGraph {
  workflowId: string;
  workflowName: string;
  workflowStatus: Workflow['status'];
  nodes: DagNode[];
  edges: DagEdge[];
}
```

### 3. API Functions
New file: `dashboard/src/api/workflows.ts`

```typescript
getWorkflows(page, size, status?, sort?) → PaginatedResponse<WorkflowSummary>
getWorkflow(id) → WorkflowResponse
createWorkflow(CreateWorkflowRequest) → WorkflowResponse
updateWorkflow(id, UpdateWorkflowRequest) → WorkflowResponse
deleteWorkflow(id) → void
executeWorkflow(id) → WorkflowResponse
pauseWorkflow(id) → WorkflowResponse
resumeWorkflow(id) → WorkflowResponse
resetWorkflow(id) → WorkflowResponse
getWorkflowDag(id) → DagGraphResponse
```

### 4. Utility Functions

#### `dashboard/src/utils/dagLayout.ts`
- Wraps dagre to compute node positions from a `DagGraph`
- Direction: **top-to-bottom** (TB)
- Node dimensions: ~180px wide, ~80px tall (dynamic based on content)
- Spacing: 50px between nodes in same layer, 80px between layers

#### `dashboard/src/utils/dagCycleCheck.ts`
- DFS-based cycle detection on edge addition
- Returns `boolean` — true if adding the edge would create a cycle
- Used both in frontend (instant feedback) and validated by backend

#### `dashboard/src/utils/statusColors.ts`
- Maps job status to Tailwind color classes
- Maps workflow status to badge colors

### 5. React Query Hooks

#### `useWorkflowDag(workflowId)`
- Wraps `useQuery` to fetch `GET /api/v1/workflows/{id}/dag`
- `refetchInterval: 3000ms` for live updates
- Returns `{ dag, isLoading, error, refetch }`

#### `useWorkflowActions(workflowId)`
- Wraps `useMutation` for execute/pause/resume/reset/delete
- Provides `execute()`, `pause()`, `resume()`, `reset()`, `remove()` functions
- Each invalidates `['workflows']` and `['workflowDag', workflowId]` queries on success

## Acceptance Criteria
- [ ] Dependencies installed and configured
- [ ] All TypeScript types defined and exported
- [ ] API functions implemented and typed
- [ ] `dagLayout` produces valid topological ordering
- [ ] `dagCycleCheck` correctly detects cycles in various graph shapes
- [ ] `statusColors` maps all job and workflow statuses to colors
- [ ] React Query hooks set up with proper polling and cache invalidation
- [ ] Unit tests for utilities pass: `dag_cycle_check.test.ts`, `dag_layout.test.ts`, `status_colors.test.ts`

## Deliverables
```
dashboard/
  package.json                          -- updated with @xyflow/react, @dagrejs/dagre

  src/api/
    workflows.ts                        -- NEW

  src/types/
    api.ts                              -- UPDATED (add workflow types)

  src/hooks/
    useWorkflowDag.ts                   -- NEW
    useWorkflowActions.ts               -- NEW

  src/utils/
    dagLayout.ts                        -- NEW
    dagCycleCheck.ts                    -- NEW
    statusColors.ts                     -- NEW

  src/test/
    utils/dag_cycle_check.test.ts
    utils/dag_layout.test.ts
    utils/status_colors.test.ts
```

## Dependencies
- Issue 038.9 (Phase 3 Integration & Testing — API)
- Existing dashboard foundation from Milestone 4
- React Query already set up in the dashboard

## Notes
- 100+ node performance: dagre layout on 100+ nodes can take ~100-300ms. Run layout in a Web Worker if needed (future optimization).
- At 3s intervals, polling the DAG endpoint for large workflows (100+ nodes) could be heavy. Consider adding `If-Modified-Since` or ETag support (future optimization).
