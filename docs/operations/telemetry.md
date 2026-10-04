# Telemetry

Sources: Spring Boot Actuator + Micrometer/Prometheus + structured Logback logs.
No request bodies, passwords, tokens, or device keys are ever logged or metered.

## Health

- `GET /api/v1/health` — public liveness (used by Docker/K8s probes).
- `GET /actuator/health` — public liveness/readiness groups; details only
  `when-authorized`. Fails (DOWN) when the Oracle datasource is unreachable —
  the app never reports healthy with a broken database.
- `GET /actuator/info` — public build info.

## Metrics (all under `/actuator/prometheus`, authentication required)

Framework (free from Boot/Micrometer):

- `http_server_requests_seconds_*` — count + latency per method/uri/status.
  Use `..._count{status="500"}` for API error count and the histogram for
  request duration.
- `jvm_*` — memory, GC, threads (uptime via `process_uptime_seconds`).
- `hikaricp_connections_*` — pool usage = database connection health.

Business (custom counters, this project):

| Metric | Incremented when | Meaning |
|---|---|---|
| `booking_conflicts_total` | overlap rejected (create or confirm) | booking conflict attempts |
| `overdue_detected_total` | sweep flips a booking to OVERDUE | overdue detections |
| `sensor_events_total{stale="true"\|"false"}` | event stored | accepted sensor events |
| `sensor_failures_total` | auth failure / future / ancient timestamp | rejected sensor input |
| `prediction_requests_total{method="empirical"\|"naive"}` | forecast served | prediction demand |
| `prediction_evaluations_total` | backtest run | evaluation demand |

Definitions: a "sensor failure" is OUR rejection of input (never a device
FAULT/OFFLINE report — those are data, counted in analytics). Counters created
at startup export `0.0`; tag-variant series (`sensor.events{stale=…}`,
`prediction.requests{method=…}`) appear on first use. Counters only
increase; rates come from Prometheus `rate()`/`increase()` queries.

Example PromQL:

```
increase(booking_conflicts_total[1h])
rate(http_server_requests_seconds_count{status="500"}[5m])
process_uptime_seconds
```

## Logs (console, UTC, `trace=` field; JSON encoder is a documented swap-in)

- INFO: startup banner, Flyway results, logins/registrations (usernames only),
  booking/QR/usage/sensor/overdue state changes, sweep summaries
  (`Overdue sweep finished: N newly overdue booking(s)`).
- WARN: stale sensor events, contradictory IN_USE/≈0 A readings, request
  errors (4xx with path, no payloads), disabled-account logins.
- ERROR: unexpected failures (500 path) with stack traces — never with
  credentials. No per-request access log (Actuator histograms cover it).

## Sensitivities (audited 2026-10-04)

`passwordHash` never leaves DTOs; QR/sensor raw secrets are logged as ids/codes
only; JWTs never logged; `app.jwt.secret` and datasource passwords come from env
and are absent from `/actuator` (env/beans endpoints are NOT exposed).
Swagger UI stays public as a capstone convenience — gate it behind auth/reverse
proxy in any real deployment.
