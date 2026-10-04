# Architecture

## Style

Modular monolith. One deployable Spring Boot API with flat, capability-named packages:

```
com.labmarket
├── controller/   # REST controllers (DTO in/out only, never entities)
├── service/      # business rules + transactions (BookingService, SensorService, ...)
├── repository/   # Spring Data JPA interfaces (+ JPQL queries)
├── entity/       # JPA entities + enums
├── dto/          # request/response records + Bean Validation
├── mapper/       # entity <-> DTO (manual)
├── security/     # JWT service/filter, device rate limiter, user details
├── exception/    # ApiError + GlobalExceptionHandler
├── config/       # security, CORS, OpenAPI, scheduling, clock
├── scheduling/   # overdue sweep job
├── prediction/   # AvailabilityPredictor interface + baseline implementations
└── util/         # shared helpers
```

## Modules (as built)

1. `auth` — users, JWT, roles STUDENT/LAB_STAFF/ADMIN
2. `equipment` — catalog + status
3. `booking` — calendar + conflict detection (equipment row lock + overlap check)
4. `qr` — single-use opaque tokens + server-validated check-in/out
5. `sensor` — untrusted simulator ingest (device keys, validated, rate-limited)
6. `usage` + `overdue` — logging, scheduler, alerts
7. `dashboard` + `prediction` — utilization + explainable baseline forecast (no fake ML)
8. cross-cutting: `audit` (append-only events), telemetry (Actuator/Prometheus)

## Key rules (as implemented)

- Controllers return DTOs; repositories return entities; services own transactions.
- Overlapping bookings are rejected with `409 Conflict` (equipment `FOR UPDATE`
  lock serializes racers — Oracle has no exclusion constraints).
- QR check-in/out validated server-side (opaque single-use token hash + expiry + window).
- Sensor input treated as untrusted: device-key auth, range checks, future/ancient
  rejection, staleness flags, rate limits.
- State changes write audit rows (failures via independent transactions).
- No secrets in git; env vars only (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, …).
- Oracle specifics: `NUMBER GENERATED … AS IDENTITY` PKs, `TIMESTAMPTZ`,
  `VARCHAR2`/`NUMBER(1)` booleans, Flyway-versioned DDL.
