#026 — Workers View: Live Worker Grid

**Milestone:** 4 — Observability Dashboard  
**Labels:** `frontend`, `ui`, `priority:high`  
**Assignee:** —  
**Estimate:** 1 day  
**Depends on:** #021  

## Description

Build the "Workers" page that displays all connected worker nodes in a responsive table or card grid with live status indicators.

### UI Requirements

| Column | Content |
|--------|---------|
| **Status** | Color-coded badge: 🟢 IDLE, 🟡 BUSY, 🔴 OFFLINE |
| **Hostname** | Worker's hostname string |
| **IP Address** | Worker's IP |
| **Last Heartbeat** | Relative time ("3 seconds ago") |
| **Uptime** | Since registration |

## Acceptance Criteria

- [ ] `/workers` route renders a table/grid of all workers
- [ ] Uses `@tanstack/react-query` `useQuery` with `refetchInterval: 2000` for live polling
- [ ] Status badges: green (IDLE), amber (BUSY), red (OFFLINE) with smooth transitions
- [ ] Empty state: "No workers connected" message when list is empty
- [ ] Loading skeleton shown during initial data fetch
- [ ] Last heartbeat displayed as relative time (e.g., "5s ago"), updated client-side every second
- [ ] Responsive: table on desktop, stacked cards on mobile

## Technical Notes

- Use `date-fns` `formatDistanceToNow()` for relative timestamps
- Consider `useMemo` to avoid unnecessary re-renders on polling
