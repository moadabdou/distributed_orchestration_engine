# Future Improvements & Enhancements

This document tracks ideas and features that fall outside the main scope of the current 7 development milestones, but are highly valuable for the long-term maturity of the engine.

## Milestone 8: Advanced Control Plane (Dashboard v2.0)
*Timeline: Scheduled for after the completion of Milestones 1-7 (specifically after Testing, Hardening, Scaling, and DevOps are finalized).*

While Milestone 4 delivered solid read-only observability and monitoring, the frontend should eventually support a full **Control Plane** to manage workflows interactively.

### Planned Features:
- **Dependency Graphs (DAG Visualizer):** A visual, interactive representation of job workflows and their dependencies (similar to Airflow's DAG UI).
- **Visual Job Scheduling & Management:** The ability to schedule, pause, resume, cancel, or retry jobs interactively from the dashboard.
- **Full System Controls:** Granular write-access to manage worker nodes (e.g., manually draining a node, forcing disconnects/reconnections, or updating worker tags directly from the UI).
- **Interactive Workflow Builder:** Drag-and-drop builders or advanced form-based job submission capabilities.

### Rationale for Deferment:
1. **MVP Focus:** The primary goal of the current roadmap is to get a robust, distributed orchestration engine deployed and hardened. Read-only observability is sufficient to monitor and debug the MVP.
2. **Backend Stability First:** Features like DAG scheduling and system control require extremely robust backend APIs, transaction management, and concurrency handling. Developing the UI for this before the backend is scaled (Milestone 5) and hardened (Milestone 7) risks significant frontend rework if backend architectural changes occur.
3. **Scope Control:** Building interactive DAG visualizers is a complex, time-consuming frontend task. Deferring this prevents it from becoming a distraction that stalls the core engine's architectural development.
