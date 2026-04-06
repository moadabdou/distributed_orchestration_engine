#027 — Jobs View: Filterable Job List with Status Badges

**Milestone:** 4 — Observability Dashboard  
**Labels:** `frontend`, `ui`, `priority:high`  
**Assignee:** —  
**Estimate:** 1 day  
**Depends on:** #021  

## Description

Build the "Jobs" page that displays all jobs with filtering, sorting, and real-time status updates.

### UI Requirements

| Column | Content |
|--------|---------|
| **ID** | Truncated UUID (first 8 chars), click to expand |
| **Status** | Color badge: ⬜ PENDING, 🔵 ASSIGNED, 🟡 RUNNING, 🟢 COMPLETED, 🔴 FAILED |
| **Worker** | Worker hostname (or "—" if unassigned) |
| **Created** | Relative timestamp |
| **Duration** | Time from created to completed (or "In progress") |

### Controls

- **Filter bar**: Dropdown to filter by status (All / Pending / Running / Completed / Failed)
- **Submit Job button**: Opens a modal to submit a new job via POST API
- **Auto-refresh indicator**: Pulsing dot showing live polling is active

## Acceptance Criteria

- [ ] `/jobs` route renders a paginated job list styled with the soft, ethereal Fern OS theme
- [ ] Live polling every 2 seconds via `useQuery` + `refetchInterval`
- [ ] Status filter: clicking a status filters the list (client-side or query param to API)
- [ ] Pagination controls: Previous / Next / page indicator
- [ ] "Submit Job" button → modal with JSON textarea → POST to API → refreshes list
- [ ] Status badges with distinct colors and smooth state transitions
- [ ] Empty state per filter: "No failed jobs" etc.
- [ ] Job row click → expands to show full payload and result

## Technical Notes

- Use `useQueryClient().invalidateQueries()` after job submission for instant refresh
- Consider optimistic updates for the submit flow
