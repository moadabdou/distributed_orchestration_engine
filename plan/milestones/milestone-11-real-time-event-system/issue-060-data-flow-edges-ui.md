# Issue #060 — Data Flow Edges Visualization (UI)

## Description
Introduce a new type of visual edge in the DAG visualizer to represent "Data Flow" or "Signaling" between jobs. This is distinct from the existing "Control Flow" edges that determine execution order.

## Rationale
Current DAG edges represent dependencies (Job B runs after Job A). However, concurrent jobs (running in parallel) might still exchange data or signals via XComs and Events. Visualizing these interactions helps users understand the actual information flow in a complex workflow.

## Requirements
- Introduce a new edge type in the UI (e.g., dashed lines, different colors, or animated "flowing" dots).
- Add support for defining "data dependencies" in the workflow definition API (purely for visualization).
- Update the Dashboard's DAG renderer to distinguish these edges from standard control flow edges.
- Provide a toggle in the UI to show/hide Data Flow edges to avoid clutter.

## Acceptance Criteria
- Dashboard can render a distinct edge type between two jobs that share a "data" relationship.
- The edges do not affect the backend's execution scheduling.
