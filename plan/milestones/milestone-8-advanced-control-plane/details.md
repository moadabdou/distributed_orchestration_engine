# Milestone 8: Advanced Control Plane (Dashboard v2.0)

## Overview

Transform the read-only dashboard into a full **Control Plane** capable of interactively managing workflows, visualizing dependency graphs, and controlling the entire system from a single UI.

This milestone delivers the features originally deferred from Milestone 4 to ensure backend stability and API maturity before building complex interactive UIs.

## Goals

1. **Dependency Graphs (DAG Visualizer)** — Interactive visual representation of job workflows and dependencies (similar to Airflow's DAG UI).
2. **Visual Job Scheduling & Management** — Schedule, cancel, cancel all remaining, or retry jobs interactively from the dashboard.
3. **Full System Controls** — Granular write-access to manage worker nodes (drain nodes, force disconnects/reconnects, update worker tags).
4. **Interactive Workflow Builder** — Drag-and-drop builders or advanced form-based job submission capabilities.
5. **UI Refinements (See and Fix)** — Perform a comprehensive visual and functional pass over the dashboard to address misalignments, scaling/responsive issues, component states, and other UX friction points.

## Architecture Decisions

| Decision | Choice | Rationale |
|---|---|---|
| DAG Library | React Flow / D3.js | Mature ecosystem, good performance for large graphs |
| State Management | Zustand or Redux Toolkit | Predictable state updates for complex UI interactions |
| WebSocket Integration | STOMP over WebSocket | Real-time updates for job state transitions |
| API Design | REST + WebSocket | REST for mutations, WebSocket for live updates |

## Success Criteria

- [ ] DAG visualizer renders workflows with clickable nodes and edges
- [ ] Users can cancel and retry jobs from the dashboard with proper validation/idempotency guarantees
- [ ] Worker nodes can be drained, disconnected, or reconnected via UI controls
- [ ] Interactive workflow builder allows creating and submitting job chains visually
- [ ] UI refined through visual audits resolving inconsistent layout/styling edge cases across the dashboard
- [ ] All control plane actions are reflected in real-time via WebSocket updates
- [ ] Backend APIs support idempotent operations to prevent duplicate actions

## Dependencies

- Milestones 1–7 complete
- Stable REST API contracts from Milestone 3
- Robust job state machine and scheduler from Milestone 2
- Observability foundation from Milestone 4

## Estimated Effort

**5–7 working days**
