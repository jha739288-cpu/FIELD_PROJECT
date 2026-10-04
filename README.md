# Usage-Based Laboratory Equipment Marketplace with Predictive Availability

Third-year B.Sc. IT capstone. Industry-style monorepo: Spring Boot 3 (Java 21) + Oracle 21c XE + React (Vite + TS).

## Architecture

```
browser :5173 → frontend (nginx static build)
browser → backend :8080 (Spring Boot, prod profile in Docker)
backend → Oracle XE :1521 (Flyway migrations V1→latest on startup)
```

## Monorepo layout

```
.
├── backend/                 # Spring Boot API (Java 21, Maven, Oracle/Flyway)
├── frontend/                # React + Vite + TypeScript SPA (nginx in Docker)
├── database/                # Authored Oracle DDL/seeds (mirrors backend Flyway versions)
├── docs/                    # Architecture, setup, API standards, evaluation, deployment
├── simulator/               # Sensor simulator (stdlib Python) + ESP32 notes
├── .github/workflows/       # CI (GitHub Actions)
├── docker-compose.yml       # Reproducible stack: Oracle XE + backend + frontend
└── .env.example             # Placeholder-only env template (never real secrets)
```

## Quick start (Docker — recommended)

Prerequisites: Docker Engine 24+ with Compose v2, 8 GB free RAM, ports
1521/8080/5173 free. Full guide + troubleshooting: `docs/deployment/docker.md`.

```bash
cp .env.example .env   # then set ORACLE_PASSWORD, DB_PASSWORD, JWT_SECRET (≥32 chars)
docker compose build
docker compose up -d
```

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- Actuator health: http://localhost:8080/actuator/health
- Oracle XE: localhost:1521 (service `XEPDB1`, data persisted in `oradata` volume)

Shutdown: `docker compose stop` (keep data) · `docker compose down` (remove containers)
· `docker compose down -v` (also wipe the database — fresh Oracle next start).

## Backend only (without Docker)

```bash
cd backend
cp ../.env.example ../.env   # or export vars manually (Windows: see docs/setup.md)
mvn spring-boot:run
```

## Frontend only

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

See `docs/setup.md` for prerequisites and Windows notes, and `docs/architecture.md` for module boundaries.

## Technology stack

Java 21 · Spring Boot 3.3 (Web, Security/JWT, Data JPA, Actuator, Validation) ·
Oracle XE 21c (Flyway V1→V11) · React 18 + Vite + TypeScript + axios +
react-router · JJWT · ZXing (QR) · Micrometer/Prometheus · Maven · Docker/Compose.

## Features

Auth (JWT, STUDENT/LAB_STAFF/ADMIN) · equipment catalog · bookings with
server-side conflict detection (row-locked) · booking calendar/availability ·
QR check-in/out (single-use hash tokens, usage sessions) · usage history ·
sensor ingest (per-device keys, simulator included) · overdue scheduler +
alerts · audit trail · utilization dashboard + analytics · explainable
prediction baseline with backtest evaluation.

## Security (highlights)

BCrypt-10; 256-bit JWT floor with prod fail-fast; per-device sensor keys;
single-use QR hashes; owner/staff object checks + method security; generic auth
errors (no enumeration); CORS allowlist; stateless CSRF-immune API; 4xx-mapped
error model (no stack/SQL leaks); rate-limited ingest; append-only audit.

## Testing

Backend: `cd backend && mvn -B test` — **210 tests, 0 failures** (H2; Oracle
proven live separately). Frontend: `npm run typecheck`, `npm run build`.
Security/robustness evidence: `docs/evaluation/security-tests.md`,
`docs/evaluation/robustness-tests.md`. Prediction methodology + limitations:
`docs/prediction/`.

## Docker

See [docs/deployment/docker.md](docs/deployment/docker.md): Oracle XE +
backend (prod) + frontend via `docker compose up -d`; Flyway migrates on boot;
health-gated startup; `.env` from `.env.example` (placeholders only).

## Oracle

System of record. Local XE 21c for development; `gvenzl/oracle-xe:21-slim` in
Compose. Schema owned by Flyway (`backend/.../db/migration`, authored copies in
`database/schema/`); never edit applied versions.

## CI/CD

`.github/workflows/ci.yml`: backend suite (H2), frontend typecheck/build/
critical-audit, Flyway migration on ephemeral Oracle XE, image builds. No
secrets in the file; per-job caching; artifacts = surefire reports. Details:
[docs/operations/ci-cd.md](docs/operations/ci-cd.md).

## Telemetry

Health: `/api/v1/health`, `/actuator/health`. Metrics (authed):
`/actuator/prometheus` — HTTP/JVM/Hikari plus `booking.conflicts`,
`overdue.detected`, `sensor.events{stale}`, `sensor.failures`,
`prediction.requests{method}`, `prediction.evaluations`. Structured UTC logs;
sweep summaries at INFO, sensor anomalies at WARN. Details:
[docs/operations/telemetry.md](docs/operations/telemetry.md).

## Local development

Backend: `cd backend && mvn spring-boot:run` (dev profile, local XE).
Frontend: `cd frontend && npm install && npm run dev` (`VITE_API_BASE_URL`).

## Environment variables

See `.env.example` (placeholders only): `ORACLE_*`, `DB_URL/DB_USERNAME/DB_PASSWORD`,
`JWT_SECRET` (≥32 chars, prod fail-fast), `CORS_ALLOWED_ORIGINS`,
`FRONTEND_API_URL`/`VITE_API_BASE_URL`. No `SENSOR_API_KEY` by design
(per-device provisioned keys instead). Never commit real `.env`.

## Deployment & troubleshooting

Compose: `docs/deployment/docker.md` (exact commands, reset, troubleshooting
table). Operations: `docs/operations/` (health, metrics interpretation,
sensor/conflict/overdue/prediction investigations, CI behavior).
