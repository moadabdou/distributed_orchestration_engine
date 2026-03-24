#028 — Dashboard Overview & Navigation Shell

**Milestone:** 4 — Observability Dashboard  
**Labels:** `frontend`, `ui`, `priority:medium`  
**Assignee:** —  
**Estimate:** 1 day  
**Depends on:** #022, #023  

## Description

Build the main dashboard landing page with summary metrics and the application shell (sidebar/navbar, routing).

### Dashboard Summary Cards

| Card | Metric |
|------|--------|
| **Total Workers** | Count of workers (with color: green if >0, red if 0) |
| **Active Workers** | Count where status = `IDLE` or `BUSY` |
| **Total Jobs** | Count of all jobs |
| **Pending Jobs** | Count where status = `PENDING` |
| **Running Jobs** | Count where status = `RUNNING` |
| **Completion Rate** | `COMPLETED / (COMPLETED + FAILED) * 100` % |

### Navigation

```
┌──────────────────────────────────────────┐
│  🔧 Orchestration Engine                 │
├──────────┬───────────────────────────────┤
│          │                               │
│ Dashboard│   [Summary Cards]             │
│ Workers  │   [Recent Activity Feed]      │
│ Jobs     │                               │
│          │                               │
└──────────┴───────────────────────────────┘
```

## Acceptance Criteria

- [ ] App shell with sidebar navigation: Dashboard, Workers, Jobs
- [ ] React Router: `/` → Dashboard, `/workers` → Workers, `/jobs` → Jobs
- [ ] Dashboard page: 6 summary metric cards, auto-refreshing
- [ ] Recent activity feed: last 10 job status changes (derived from jobs sorted by `updatedAt`)
- [ ] Active route highlighted in sidebar
- [ ] Dark/light theme toggle (persisted in `localStorage`)
- [ ] Professional branding: app title, subtle logo placeholder
- [ ] Responsive: sidebar collapses to hamburger on mobile
