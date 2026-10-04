# Docker / reproducible deployment

```
browser :5173 → frontend (nginx, static build)
browser → backend :8080 (Spring Boot, prod profile) → Oracle XE :1521
```

Compose starts Oracle first (health-gated), then the backend (Flyway migrates
V1→latest, then health-gated), then the static frontend.

## Oracle decision (recorded)

Oracle XE is free for development/demo use (OTN license). The compose file uses
the widely-used community image `gvenzl/oracle-xe:21-slim`, which provisions the
`XEPDB1` pluggable database plus our schema owner via `APP_USER`/`APP_USER_PASSWORD`.
This keeps one-command reproducibility **without changing the application**:
the backend still speaks Oracle SQL (identity columns, `TIMESTAMPTZ`, Flyway).

If your environment cannot pull that image, use the two-part deployment instead:

1. Install Oracle XE 21c locally (Windows installer or RPM).
2. Create the schema owner manually (see below) and set `DB_URL` to
   `jdbc:oracle:thin:@host.docker.internal:1521/XEPDB1` (or `localhost:1521/...`
   for non-Docker runs).
3. Start only backend + frontend: `docker compose up --build backend frontend`.

## Prerequisites

- Docker Engine 24+ with Compose v2 (Windows: Docker Desktop).
- 8 GB RAM free (Oracle XE wants ~2 GB; first boot is slow).
- Ports free: 1521 (Oracle), 8080 (API), 5173 (web).
- No Docker daemon on the reference dev box at authoring time — container runs
  below were validated as far as possible without one (see Limitations).

## Steps (clean machine)

```bash
git clone <repo-url> && cd <repo>
cp .env.example .env
# Edit .env: ORACLE_PASSWORD, DB_PASSWORD, JWT_SECRET (≥32 chars). Nothing else required.
docker compose build
docker compose up -d
docker compose ps                    # oracle healthy → backend healthy → frontend up
curl http://localhost:8080/api/v1/health        # {"status":"UP",...}
curl http://localhost:8080/actuator/health     # {"status":"UP",...}
```

Open http://localhost:5173 (login/register), Swagger at
http://localhost:8080/swagger-ui.html.

Smoke-test the API (register → login → equipment → booking → dashboard):

```bash
T=$(curl -s -X POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","email":"demo@example.com","password":"password123"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -s localhost:8080/api/v1/equipment?size=5 -H "Authorization: Bearer $T"
curl -s localhost:8080/api/v1/dashboard/summary -H "Authorization: Bearer $T" # 403: staff only, proves RBAC
```

Staff data (equipment, sensor keys) needs a LAB_STAFF/ADMIN grant — same as
non-Docker runs (see backend README).

## Stop / remove / reset

```bash
docker compose stop     # stop, keep data
docker compose down     # stop + remove containers/network (keeps the oradata volume)
docker compose down -v  # also deletes ALL database data (fresh Oracle next start)
```

Migrations are Flyway-owned (`backend/.../db/migration/V*.sql`, mirrored under
`database/schema/`). The backend applies them at startup in version order; never
edit an applied version. `down -v` gives a truly fresh database (verified
procedure: V1→V11 applied cleanly on an empty schema — see below).

## Migration verification (fresh-schema proof)

Performed 2026-10-04 against local Oracle XE 21c with a scratch user
`labmarket_verify` (backend, `prod` profile):

1. `CREATE USER labmarket_verify ...; GRANT CONNECT, RESOURCE;` + quota.
2. Booted backend with `DB_USERNAME=labmarket_verify`.
3. Result: Flyway applied V1..V11 + `R__seed_roles` (12 history rows), JPA
   `ddl-auto: validate` passed, `/api/v1/health` → UP, seed roles present.
4. Dropped the scratch user afterwards.

So a first-ever `docker compose up` (empty `oradata` volume) migrates identically.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Oracle container restarting / unhealthy | Needs ~2 GB RAM and minutes on first boot (`start_period` covers this). Check `docker logs labmarket-oracle`; on Apple Silicon/ARM use an x86 emulator or the two-part deployment (XE image is x86_64). |
| Backend exits: `ORA-01017 invalid username/password` | `DB_USERNAME`/`DB_PASSWORD` in `.env` don't match `APP_USER*` given at Oracle's first boot. Credentials are read once at volume creation — after changing them, `down -v` (wipes data) or `ALTER USER` inside Oracle. |
| Backend exits: Flyway checksum mismatch | A migration file was edited after applying. Never edit applied versions; add a new `V*.sql`. |
| Backend health DOWN (DB) | Oracle not healthy yet or wrong `DB_URL`. In compose the host is `oracle`, not `localhost`. |
| Frontend shows API errors / blank lists | `FRONTEND_API_URL` was wrong at build time (it is baked in). Rebuild: `docker compose build frontend`. Browser must reach the backend host directly (CORS allows `CORS_ALLOWED_ORIGINS`). |
| Port conflict (`address already in use`) | Local Oracle/backend running: stop them or remap ports in compose. |
| `JWT_SECRET` error on start | Missing or <32 chars in `.env` (fail-fast by design). |
| Stale containers after editing compose | `docker compose down && docker compose up -d --build`. |
| Login works but `/dashboard/*` 403s | Correct behavior: staff/admin only. Register creates STUDENT. |

## Container security choices (proportionate)

- Backend runs as non-root `app` (Dockerfile `USER app`); frontend uses the
  unprivileged nginx image (port 8080).
- No secrets in images: everything via environment; `.env` is gitignored and
  only `.env.example` (placeholders) is committed.
- Minimal surface: Oracle exposes only 1521, backend only 8080, frontend only 8080→5173;
  no privileged flags, no extra capabilities, no host mounts except the `oradata` volume.
- Prod profile fails fast without `DB_*`/`JWT_SECRET`; actuator only exposes
  health/info/metrics/prometheus (metrics need auth).
