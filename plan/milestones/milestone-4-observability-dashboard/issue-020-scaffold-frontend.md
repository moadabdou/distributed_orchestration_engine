#024 — Scaffold React + TypeScript Frontend Project

**Milestone:** 4 — Observability Dashboard  
**Labels:** `frontend`, `setup`, `priority:high`  
**Assignee:** —  
**Estimate:** 0.5 day  

## Description

Initialize a new React + TypeScript SPA using Vite in a `dashboard/` directory at the project root.

## Acceptance Criteria

- [ ] Project created via `npm create vite@latest dashboard -- --template react-ts`
- [ ] Directory structure: `dashboard/src/`, `dashboard/public/`, `dashboard/tsconfig.json`
- [ ] TypeScript strict mode enabled (`"strict": true` in `tsconfig.json`)
- [ ] Install core dependencies: `axios` (or fetch wrapper), `react-router-dom`, `@tanstack/react-query`
- [ ] ESLint + Prettier configured with TypeScript rules
- [ ] `npm run dev` serves the app on `http://localhost:5173` with HMR
- [ ] Placeholder `App.tsx` renders "Orchestration Dashboard" heading
- [ ] Proxy config: Vite dev server proxies `/api` → `http://localhost:8080` (Spring Boot)

## Technical Notes

- Use `vite.config.ts` `server.proxy` for API proxying during development
- Add a `types/` directory for shared TypeScript interfaces from day one
