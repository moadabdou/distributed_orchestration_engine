# Milestone 4: TypeScript Observability Dashboard

## Overview

Build a modern, real-time web dashboard for **Fern OS** that visualizes the state of the entire cluster — workers, jobs, and system health — by consuming the Spring Boot REST API.

This is the user-facing layer. It transforms raw JSON data into an intuitive, live control panel that makes the distributed system **observable**.

## UI/UX Design Language (Frieren-inspired)

The design must embody the serene, gentle, and ethereal vibe of Frieren (from *Sousou no Frieren*), aligning with the **Fern OS** identity.
- **Color Palette:** Soft, light, and gentle tones. Use Frieren's signature crisp whites, pale aquamarine/mint greens (fern), soft lavender or pale purple accents, with subtle warm golds for highlights or statuses.
- **Aesthetic:** A "glassy, light, and gentle feeling." Utilize **Glassmorphism** (translucent frosted-glass panels with soft blurs and subtle white borders) floating over bright, airy, or subtle gradient backgrounds to create depth and elegance.
- **Vibe:** Calm, magical, and clean. Despite being a system orchestration tool, the interface should feel tranquil, organized, and not overly aggressive or industrial—like a quiet, well-tended magical forest.

## Goals

1. **Project scaffold** — React (or Vue) SPA with TypeScript, Vite build tooling.
2. **API client layer** — Typed HTTP client consuming `/api/v1/jobs` and `/api/v1/workers`.
3. **Workers view** — Real-time table of connected workers with status indicators (IDLE / BUSY / OFFLINE).
4. **Jobs view** — Filterable, sortable list of jobs with status badges and live transitions.
5. **Live polling** — Auto-refresh every 2 seconds for near real-time updates.
6. **Dashboard overview** — Summary cards showing total workers, active jobs, completion rate, etc.

## Architecture Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Framework | React 18+ with TypeScript | Industry standard, large ecosystem, recruiter recognition |
| Build tool | Vite | Fast HMR, modern ESM-first bundler |
| HTTP client | Axios or Fetch + custom wrapper | Type-safe API layer with interceptors |
| Styling | CSS Modules or Tailwind CSS | Scoped styles, rapid prototyping |
| State management | React Query (TanStack Query) | Built-in polling, caching, loading states |

## Success Criteria

- [ ] Dashboard loads and displays worker grid with live status
- [ ] Jobs list shows all jobs with `PENDING` / `RUNNING` / `COMPLETED` / `FAILED` badges
- [ ] New job submitted via API appears in the dashboard within 2–4 seconds
- [ ] Worker going offline reflected in the dashboard within 2 polling cycles
- [ ] Responsive layout — usable on desktop and tablet

## Dependencies

- Milestone 3 fully complete (REST API available)
- Node.js 18+ and npm/pnpm

## Estimated Effort

**4–5 working days**
