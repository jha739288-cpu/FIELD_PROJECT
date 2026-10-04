# API security tests (measured live)

Backend: Spring Boot on `:8080`, Oracle XE. Every row below was executed with
curl against the running API on 2026-10-04; expected vs actual status recorded.
Automated equivalents exist in `backend/src/test` (MockMvc, H2) and all pass.

| # | Test | Setup / input | Expected | Actual |
|---|---|---|---|---|
| 1 | Unauthenticated request | `GET /api/v1/equipment` without token | 401 | 401 ✅ |
| 2 | Student → staff endpoint | STUDENT `GET /api/v1/alerts` | 403 | 403 ✅ |
| 3 | Student → admin-only mutation | STUDENT `POST /api/v1/equipment` | 403 | 403 ✅ |
| 4a | Cancel another user's booking | STUDENT cancels victim's booking | 403 | 403 ✅ |
| 4b | Read another user's booking | STUDENT `GET` victim's booking | 403 | 403 ✅ |
| 5 | Student resolves alert | STUDENT `PUT /api/v1/alerts/1/resolve` | 403 | 403 ✅ |
| 6 | Invalid JWT | `Bearer invalid.token.here` | 401 | 401 ✅ |
| 7 | Tampered JWT | valid token + `xx` suffix | 401 | 401 ✅ |
| 8 | Invalid equipment ID | `GET /api/v1/equipment/999999` | 404 | 404 ✅ |
| 9 | Invalid booking ID | `GET /api/v1/bookings/999999` | 404 | 404 ✅ |
| 10 | Invalid QR code | check-in with `{"qrToken":"nope"}` | 404 | 404 ✅ |
| 11 | Reused QR code | check-in → 200, rescan same token | 409 "already been used" | 409 ✅ |
| 12 | Sensor, invalid equipment | valid key, code `NOPE-999` | 401 (no existence oracle) | 401 ✅ |
| 13 | Malformed sensor payload | `status: MELTDOWN`, missing timestamp | 400 | 400 ✅ |
| 14 | Invalid prediction params | `from` after `to` | 400 | 400 ✅ |
| 15 | Invalid date range | `GET /dashboard/utilization?from=…` (missing `to`) | 400 | 400 ✅ |
| 16 | Invalid slot duration | `slotMinutes=5` | 400 | 400 ✅ |
| + | Duplicate registration | same username, other email | 409 generic message | 409 `"Username or email is already registered"` ✅ |

Notes:

- Unknown sensor codes return **401, not 404** (fixed in this phase): previously the
  lookup-then-authenticate order let keyless callers distinguish valid equipment
  codes from invalid ones. `SensorService.ingest` now rejects unknown codes with
  `SensorUnauthorizedException` and audits the attempt.
- Duplicate-registration errors were unified to one message for the same reason
  (username/email enumeration). Login was already generic.
- `PUT /api/v1/usage/{id}` and `DELETE /api/v1/usage/{id}` (no such routes) return
  405 via the new `HttpRequestMethodNotSupportedException` handler (previously 500).
- Unknown `?sort=` properties return 400 (`InvalidDataAccessApiUsageException`
  handler; previously 500). Deleting equipment with bookings returns 409 — enforced
  by an explicit `existsByEquipmentId` domain check plus a `DataIntegrityViolation`
  handler as backstop (previously 500 on some databases).
