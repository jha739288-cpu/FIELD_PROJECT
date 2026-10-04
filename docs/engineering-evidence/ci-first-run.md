# CI first-run procedure (to be executed on GitHub — never run yet)

1. Push `main` (or open any PR): the `ci` workflow starts automatically.
2. Actions tab → select the run → watch the four jobs:
   `backend-test`, `frontend`, `oracle-migration`, `docker-build`.
3. Wait for all green. `oracle-migration` takes minutes (XE first boot).
4. Record: run date, commit SHA, per-job durations. Screenshot the green run
   for the report appendix.
5. On red: open the failing job log, fix in a branch, PR again. Common causes:
   - `oracle-migration`: XE boot slower than health retries on a busy runner.
   - `frontend` audit: a NEW critical advisory (moderate/high are excluded by design).
   - `docker-build`: Dockerfile drift (validate with `docker compose build` locally).

Expected: all four green. Do NOT claim green CI until this run exists.
