# Issue 038.14: Phase 4 Integration & Testing — Frontend

## Phase
**Phase 4: Frontend — DAG Visualizer & Graphical Control**

## Description
Consolidate all Phase 4 components, run comprehensive component and page tests, and perform manual end-to-end verification of the DAG visualizer and workflow management features.

## Scope

### 1. Test Suite

| Test File | What It Tests |
|-----------|---------------|
| `dag_cycle_check.test.ts` | Unit test — cycle detection on various graph shapes (linear, diamond, cycle) |
| `dag_layout.test.ts` | Unit test — layout produces valid topological ordering, handles 100+ nodes |
| `status_colors.test.ts` | Unit test — all status → color class mappings |
| `DagJobNode.test.tsx` | Component test — renders with correct colors, labels, status dots |
| `DagPreviewPanel.test.tsx` | Component test — loading state, empty state, populated state, edit button |
| `DagEditorModal.test.tsx` | Component test — editing disabled when running, save works when draft/paused, add/delete nodes and edges |
| `DagDetailPanel.test.tsx` | Component test — displays job metadata correctly |
| `DagNodeEditor.test.tsx` | Component test — inline node editing form, validation, save |
| `useWorkflowDag.test.tsx` | Hook test — query setup, polling, error handling |
| `WorkflowsView.test.tsx` | Page test — list workflows, filter, create, actions |

### 2. Manual E2E Verification
Test the following scenarios manually:

1. **Create & Execute Workflow:**
   - Create a new workflow with a diamond DAG (A→B, C→D)
   - Execute the workflow
   - Observe nodes changing color in real-time (PENDING → RUNNING → COMPLETED)
   - Verify workflow reaches COMPLETED status

2. **Edit Workflow (DRAFT):**
   - Create a workflow (DRAFT)
   - Open editor, add nodes, create edges
   - Save, verify changes persist
   - Delete a node, save, verify changes persist

3. **Edit Workflow (RUNNING — Read-Only):**
   - Execute a workflow
   - Open editor while RUNNING
   - Verify editing is disabled, banner shows message
   - Verify clicking nodes opens detail panel

4. **Workflow Actions:**
   - Pause a RUNNING workflow
   - Resume the PAUSED workflow
   - Reset a FAILED workflow to DRAFT
   - Delete a DRAFT workflow

5. **WorkflowsView Page:**
   - List workflows with filtering by status
   - Search by name
   - Pagination works correctly
   - Actions (view, execute, pause, resume, reset, delete) work from the table

6. **Large Workflow Performance:**
   - Load a workflow with 100+ nodes
   - Verify rendering is smooth (no noticeable lag)
   - Verify layout completes in reasonable time (<1s)

### 3. Integration Checklist
- [ ] All component and page tests pass
- [ ] Manual E2E verification completed
- [ ] Performance with 100+ nodes acceptable
- [ ] Build succeeds (`npm run build`)
- [ ] Code review completed
- [ ] Merged to main branch

## Acceptance Criteria
- [ ] All 10 test files implemented and passing
- [ ] Manual E2E verification scenarios all pass
- [ ] Large workflow (100+ nodes) performance acceptable
- [ ] Phase 4 gate met: "DAG visualizer + graphical workflow control" working
- [ ] Build succeeds, manual E2E verification passes

## Deliverables
```
dashboard/
  src/test/
    utils/dag_cycle_check.test.ts
    utils/dag_layout.test.ts
    utils/status_colors.test.ts
    components/DagJobNode.test.tsx
    components/DagPreviewPanel.test.tsx
    components/DagEditorModal.test.tsx
    components/DagDetailPanel.test.tsx
    components/DagNodeEditor.test.tsx
    hooks/useWorkflowDag.test.tsx
    pages/WorkflowsView.test.tsx

docs/
  phase-4-test-report.md              -- summary of test results
  phase-4-e2e-guide.md                -- manual E2E verification guide
```

## Dependencies
- Issue 038.10 (Frontend Foundation — Types, API Functions & Utilities)
- Issue 038.11 (DAG Preview Panel & Workflows View Page)
- Issue 038.12 (DAG Editor Modal — Read-Only & Visual Controls)
- Issue 038.13 (DAG Editor — Interactive Node & Edge Management)
- Issue 038.9 (Phase 3 Integration & Testing — API) — backend must be ready

## Notes
- Each phase is a mergeable increment
- Phase 4 gate: All tests pass, build succeeds, manual E2E verification
- 100+ node performance: React Flow virtualizes rendering, but dagre layout on 100+ nodes can take ~100-300ms. Run layout in a Web Worker if needed (future optimization).
- Polling overhead: at 3s intervals, polling the DAG endpoint for large workflows (100+ nodes) could be heavy. Consider adding `If-Modified-Since` or ETag support (future optimization).
