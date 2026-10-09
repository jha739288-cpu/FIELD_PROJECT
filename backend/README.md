# Backend — labmarket-backend

Spring Boot 3.3 + Java 21 + Oracle Database. Foundation only: config, health,
exception handling, security baseline. No business modules yet.

## Package layout

```
com.labmarket
├── controller/   # REST controllers (DTO in/out only)
├── service/      # business rules (arrives with Module 1+)
├── repository/   # Spring Data JPA interfaces (arrives with Module 1+)
├── entity/       # JPA entities (arrives with Module 1+)
├── dto/          # request/response records + validation
├── mapper/       # entity <-> DTO (arrives with Module 1+)
├── security/     # JwtProperties now; JWT filter + UserDetailsService in Module 1
├── exception/    # ApiError + GlobalExceptionHandler
├── config/       # SecurityConfig, OpenApiConfig, CorsConfig
└── util/         # DateTimeUtils, shared helpers
```

## Prerequisites

- JDK 21+, Maven 3.9+
- Oracle 21c XE (or any Oracle 11g+): a pluggable DB such as `XEPDB1`
- Create the dev user (example):

```sql
ALTER SESSION SET CONTAINER = XEPDB1;
CREATE USER labmarket IDENTIFIED BY "change-me-in-dev";
GRANT CONNECT, RESOURCE TO labmarket;
ALTER USER labmarket QUOTA UNLIMITED ON USERS;
```

## Configuration (environment variables — never commit secrets)

| Variable | dev default | prod |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` |
| `DB_URL` | `jdbc:oracle:thin:@localhost:1521/XEPDB1` | **required** |
| `DB_USERNAME` | `labmarket` | **required** |
| `DB_PASSWORD` | `change-me-in-dev` | **required** |
| `JWT_SECRET` | placeholder (32+ chars) | set a real 256-bit secret |
| `SERVER_PORT` | `8080` | `8080` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | your frontend origin |

Windows (PowerShell):

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DB_URL="jdbc:oracle:thin:@localhost:1521/XEPDB1"
$env:DB_USERNAME="labmarket"
$env:DB_PASSWORD="change-me-in-dev"
```

## Run

```bash
cd backend
mvn spring-boot:run
```

- Health: http://localhost:8080/api/v1/health (public)
- Actuator: http://localhost:8080/actuator/health
- Swagger: http://localhost:8080/swagger-ui.html

## Auth (Module 1)

- `POST /api/v1/auth/register` → 201 + JWT (creates USER by default, VENDOR on request; ADMIN never self-assignable → 400). Duplicate username/email → 409, bad input → 400.
- `POST /api/v1/auth/login` → 200 + JWT. Wrong credentials or disabled account → 401.
- `GET /api/v1/auth/me` → own profile (Bearer JWT). No token → 401, insufficient role on `@PreAuthorize` endpoints → 403.
- Passwords: BCrypt-hashed before persistence; responses never contain hashes. `JWT_SECRET` env in prod (min 32 chars).

## Equipment catalog

- `POST /api/v1/equipment` → 201 (VENDOR/ADMIN). `GET /api/v1/equipment` (any authenticated user; `page/size/sort`, `status`/`category`/`laboratory` filters, free text, `mine=true` for own listings) → paged envelope `{content, page, size, totalElements, totalPages}`. Vendors may mutate only items they listed (`created_by`); ADMIN unrestricted.
- `GET /api/v1/equipment/{id}` → 200 / 404. `PUT /api/v1/equipment/{id}` → 200 (code immutable). `DELETE` → 204, blocked with 409 while `IN_USE`/`RESERVED`/`OVERDUE`.
- Rule: `AVAILABLE` requires `OPERATIONAL` maintenance status. Bad enum filter → 400.

## Booking

- `POST /api/v1/bookings` → 201 PENDING (any authenticated user). `GET /api/v1/bookings` (own for users; own-equipment for vendors; all + `equipmentId`/`status` filters for admins), `GET /api/v1/bookings/my`, `GET /api/v1/bookings/{id}` (owner/vendor-of-item/admin).
- `PUT /api/v1/bookings/{id}/cancel` (owner while PENDING/CONFIRMED, or staff/admin), `/confirm` + `/reject` (staff/admin).
- Overlap rule: same equipment must not have overlapping CONFIRMED/CHECKED_IN/OVERDUE bookings (half-open ranges; back-to-back allowed). Enforced by equipment row lock (`FOR UPDATE`) + active-booking check → 409 with a clear message. Unbookable equipment, past starts, end ≤ start → 400/409.

## Booking calendar (read-only, any authenticated user)

- `GET /api/v1/bookings/equipment/{id}?from=&to=` — schedule lane (identity, live statuses, slots; purpose/username redacted for non-owners).
- `GET /api/v1/bookings/equipment/{id}/availability?from=&to=` — `bookable`, `bookedPeriods`, `pendingPeriods`, computed `freePeriods` gaps, `maintenance` flag + message, current statuses.
- `GET /api/v1/bookings/calendar?from=&to=[&equipmentId=]` — lanes for one or all items. Windows capped at 93 days; from ≥ to → 400.
- Booking times are normalized to millisecond precision (DB timestamp rounding would otherwise create phantom micro-overlaps between back-to-back bookings).

## QR check-in / check-out

- `POST /api/v1/bookings/{id}/qr` → 201 single-use opaque token (owner/staff/admin, CONFIRMED only; only SHA-256 hash stored, prior unused tokens invalidated).
- `POST /api/v1/bookings/check-in` `{qrToken}` → 200 (owner only; booking/equipment resolved server-side, never trusted from client). Success: booking → CHECKED_IN, equipment → IN_USE, usage session opened — atomically.
- `PUT /api/v1/bookings/{id}/checkout` → 200 (owner/staff/admin): session closed with `durationSeconds`, booking → COMPLETED, equipment → AVAILABLE (never clobbers staff-set states).
- Rejections: unknown token → 404, expired → 410, used → 409, wrong user → 403, too-early / past-window / cancelled / unbookable equipment → 409.
- Audit (`audit_events`, REQUIRES_NEW so failures persist): `QR_GENERATED`, `CHECKED_IN`, `CHECKED_OUT`, `QR_VALIDATION_FAILED`.

## Usage log

`usage_sessions` is the single usage store (`source`: QR_CHECKIN/SENSOR/MANUAL; `status`: ACTIVE/COMPLETED; MANUAL rows have no booking).

- `GET /api/v1/usage` — own rows for users; all + `equipmentId`/`userId`/`status`/`source`/`laboratory`/`from`/`to` filters for vendors/admins.
- `GET /api/v1/usage/my`, `GET /api/v1/usage/{id}` (owner/staff/admin).
- `POST /api/v1/usage` → 201 staff/admin MANUAL record (historical only: end ≤ now; duration computed; born COMPLETED).
- No update/delete routes — COMPLETED rows are immutable (wrong methods → 405).
- QR check-in/out additionally write `USAGE_STARTED`/`USAGE_ENDED` audits.

## Sensor ingest (simulator-ready, ESP32-compatible)

- Devices authenticate per item with `X-Sensor-Key` (SHA-256 stored; provision/rotate via `POST /api/v1/equipment/{id}/sensor-key`, staff/admin, raw key shown once). No user JWT required on ingest.
- `POST /api/v1/sensors/events` → 201 `{equipmentCode, status, currentValue, timestamp}`. Validated: known code (404), key (401), enum/range (400), future/too-old timestamps (400); >5 min old stored with `stale: true`.
- Status mapping: IN_USE (with ≥0.10 A draw) → IN_USE; IDLE → AVAILABLE (unless a checked-in booking owns it); OFFLINE → SENSOR_OFFLINE; FAULT → MAINTENANCE. `MAINTENANCE` items never auto-change; contradictory IN_USE/0 A ignored.
- `GET /api/v1/sensors/events?equipmentCode=` history (staff/admin). Every accepted event audited (`SENSOR_EVENT`).
- Standalone simulator: `simulator/sensor_sim.py` (stdlib only; scenarios normal/idle/offline/invalid/delayed) + ESP32 replacement notes. It is explicitly not hardware.

## Overdue detection (automated)

- `@Scheduled` sweep (`OverdueScheduledJob`, default every 5 min via `OVERDUE_CHECK_INTERVAL_MS`; disabled in tests) calls `OverdueService.detectOverdue()` with an injectable `Clock`.
- Live CONFIRMED/CHECKED_IN bookings past their end → OVERDUE; equipment follows (IN_USE/RESERVED/AVAILABLE → OVERDUE; staff/sensor states untouched); exactly one OPEN alert + one `OVERDUE_DETECTED` audit per booking — reruns are no-ops.
- `GET /api/v1/alerts` (type/status filters), `GET /api/v1/alerts/overdue` (open overdue), `PUT /api/v1/alerts/{id}/resolve` (staff/admin, optional note) — resolution closes open sessions (COMPLETED) or cancels never-used bookings, frees the equipment, writes `ALERT_RESOLVED`.

## User roles, equipment marketplace & admin

- Roles are `ADMIN` / `USER` / `VENDOR` (renamed from STUDENT/LAB_STAFF in V13;
  existing memberships preserved). Register with `role: USER` (default) or
  `VENDOR`; ADMIN is never self-assignable.
- Equipment carries marketplace fields (`specifications`, `price_per_hour`,
  `quantity`, `usage_instructions`, `safety_info`, `image_url`) plus
  `created_by` ownership: vendors mutate only their own items, admins anything.
- Account admin: `GET /api/v1/users` (search/role/status), `GET /{id}`,
  `PUT /{id}/status`, `PUT /{id}/roles` (never your own), `GET /users/vendors`.
- Analytics: `GET /api/v1/dashboard/admin/overview` (admin), `GET
  /api/v1/dashboard/vendor` (own marketplace numbers).
- Seeds (env-gated, dev/demo only): `APP_ADMIN_USERNAME`/`APP_ADMIN_PASSWORD`
  for the first admin; `APP_SEED_DEMO=true` (+ `APP_SEED_DEMO_PASSWORD`) for a
  sample vendor + 10 instruments.

- `GET /api/v1/dashboard/summary` — fleet snapshot + trailing-30-day activity + utilization % + conflict attempts.
- `GET /api/v1/dashboard/utilization?from&to[&equipmentId]` — merged per-item usage over capacity (non-operational items excluded, still listed).
- `GET /api/v1/dashboard/equipment/{id}`, `/usage` (daily trend), `/conflicts`, `/bookings` (status/day/equipment mix), `/sensors` (mix, freshness reliability, per-item).
- `GET /api/v1/dashboard/my-summary` — any authenticated user; own bookings/usage only.
- Formulas, windows, edge cases and limitations: `docs/evaluation/utilization.md`, `docs/evaluation/dashboard-metrics.md`.

The app starts with an empty schema: `ddl-auto: validate` with no entities
passes trivially, and Flyway's `V1__baseline.sql` (`SELECT 1 FROM DUAL`) needs
no business tables.

## Test

```bash
cd backend
mvn test      # H2 in-memory, no Oracle needed
mvn verify    # full build + tests
```
