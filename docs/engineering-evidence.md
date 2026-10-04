# Engineering evidence

## Repository state (honest record, 2026-10-04)

- The worktree at `FIELD PROJECT/` is **not a git repository** (`git rev-parse`
  fails; no `.git/`). There are therefore **no branches, commits, issues, pull
  requests, or code reviews to show** — and none are fabricated here.
- What exists as evidence instead:
  - **210 automated backend tests** (`mvn -B test`, all green 2026-10-04),
    covering every module including security, concurrency, and robustness
    regressions added in the quality phase.
  - **Frontend typecheck + production build** green (`tsc --noEmit`, `vite build`).
  - **Live verification logs**: each module was exercised against Oracle XE with
    curl; results are recorded in `docs/evaluation/` (`security-tests.md`,
    `robustness-tests.md`, plus the earlier module notes). These files are the
    test-evidence trail until CI exists.
  - **Docs**: architecture, setup, API standards, per-module READMEs,
    `docs/evaluation/*`, `docs/prediction/*`.

## Still to perform manually in GitHub (not done by the agent)

1. `git init`, initial commit of the cleaned tree (verify no `.env`, no secrets —
   only `.env.example` files exist; `.gitignore` now covers `.env` and `.env.*`).
2. Create the repository on GitHub and push (`main` branch protection recommended).
3. Open issues per remaining work item (Docker first-run on a Docker host, COMSPEC
   fix note for Windows dev machines, Python for the simulator).
4. Open pull requests per module for review instead of committing to `main`
   directly; require the backend suite + frontend build as merge checks.
5. Enable GitHub Actions and watch the first run: `backend-test` (H2),
   `frontend` (typecheck/build/critical-audit), `oracle-migration` (Flyway on
   ephemeral XE), `docker-build`. The workflow has never executed (no repo yet) —
   do NOT claim green CI until it does.
6. Record code-review approvals on the PRs; keep this file updated with links.

## Actual verified evidence (2026-10-04, no Docker daemon on this box)

- Backend `mvn -B test`: **210/210, 0 failures** (log retained in build output).
- Frontend `tsc --noEmit` clean + `vite build` succeeds.
- `ci.yml` + `docker-compose.yml` parse as valid YAML (js-yaml), zero PostgreSQL
  references; workflow contains no secrets (throwaway CI-only values only).
- Live Oracle XE: `/actuator/prometheus` exposes `booking_conflicts_total 1.0`
  after a real 409, `prediction_requests_total{method=empirical} 1.0` after a real
  forecast, `overdue_detected_total`/`sensor_failures_total` at 0.0; metrics
  endpoint requires auth (401 anonymous).
- Prior live evidence retained: per-module curl verifications in
  `docs/evaluation/` (security, robustness), migration V1→V11 fresh-schema proof,
  scheduler/audit behavior.
