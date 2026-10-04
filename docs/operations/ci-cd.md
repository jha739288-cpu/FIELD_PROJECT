# CI/CD (`.github/workflows/ci.yml`)

Triggers: push/PR to `main`. Four independent jobs (no `continue-on-error`
anywhere — any red job fails the run).

| Job | What | Fails when |
|---|---|---|
| `backend-test` | `mvn -B verify` on JDK 21, `SPRING_PROFILES_ACTIVE=test` (H2 — no Docker needed, deterministic). Uploads surefire reports (7-day retention). | compilation / any test fails |
| `frontend` | `npm ci`, `npm run typecheck`, `npm audit --audit-level=critical`, `npm run build` | typecheck / build / critical audit fails |
| `oracle-migration` | `gvenzl/oracle-xe:21-slim` service (health-gated) + official `flyway/flyway:10` image applies `backend/.../db/migration` V1..latest | any migration fails |
| `docker-build` | `docker compose build backend frontend` (validates both Dockerfiles; no push) | either image fails to build |

Caching: `setup-java` (Maven) + `setup-node` (npm, locked by `package-lock.json`).
Secrets: none — the Oracle service uses throwaway CI-only credentials
(`labmarket_ci`/`ci-app-pw`), the backend tests use the H2 profile's dummy JWT.
Real credentials never appear in workflow files.

## Intentional exclusions (documented)

- `npm audit` gates on **critical only**: the known moderate/high findings are
  dev-toolchain-only (vite/esbuild dev server, react-router) and fixable solely
  by breaking majors — see phase report. Production `npm run build` output
  ships no dev server.
- No OWASP backend scan: needs an NVD API key and network; dependency versions
  were reviewed manually (Boot 3.3.5 / JJWT 0.12.6 / springdoc 2.6.0 / ZXing
  3.5.3 / ojdbc 23.3 — all current patch lines, no known critical CVEs).
- No E2E browser tests: out of scope for the capstone; API coverage is 210 tests.

## What CI does NOT prove (requires a Docker host / Oracle XE locally)

`docker compose up` end-to-end, first-boot migration timing, ARM behavior —
covered in `docs/deployment/docker.md` instead.
