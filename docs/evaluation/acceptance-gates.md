# Acceptance gates

PASS = genuinely verified (test and/or live evidence). PENDING = requires an
external environment (Docker host, GitHub) or artifact not yet produced.
No measured values are invented; see `docs/evaluation/` for evidence.

| # | Gate | Requirement | Metric / target | Test method | Evidence | Status |
|---|---|---|---|---|---|---|
| 1 | Equipment catalog | CRUD + filters, staff-only mutations | 15/15 API tests; live 201/403/404/409 | MockMvc + curl | PASS |
| 2 | Booking conflicts | overlapping confirmed bookings impossible, incl. races | 409 always; concurrency test 1-confirm-only | unit + race test + live | PASS |
| 3 | QR check-in | single-use server-validated tokens, status flips | 16/16 API tests; live IN_USE→AVAILABLE | MockMvc + curl | PASS |
| 4 | Usage logging | auto duration, immutable history, role scoping | 7+9 tests; live 3600 s record | MockMvc + curl | PASS |
| 5 | Overdue detection | sweep flips past-end live bookings, one alert each | 7+6+1 tests; live auto-flip observed | MockMvc + curl + logs | PASS |
| 6 | Sensor reliability | validated ingest, safe mapping, `reliability` = fresh ÷ total | 10+12 tests; live fault/offline runs | MockMvc + curl | PASS |
| 7 | Audit completeness | every transition + every rejection recorded, rollback-proof | scoped assertions; live table reads | tests + SQL | PASS |
| 8 | Utilization accuracy | merged, clipped, maintenance-aware math | 12 service tests; hand-verified live (3602 s → 0.07%) | tests + curl | PASS |
| 9 | Predictive availability | bounded probs, known-booking precedence, evaluation | 18 tests; live 0.858/0.0 + Brier 0.175 measured | tests + curl | PASS |
| 10 | Security | 401/403/404/409 matrix, no enumeration, no leaks | 16 live checks + handlers + grep audits | curl + tests | PASS |
| 11 | CI | 4 green jobs on push/PR | workflow file valid; backend 210 + frontend green locally | local runs | PENDING (needs GitHub run) |
| 12 | Deployment | `compose up` → healthy Oracle+API+web | compose+YAML validated; V1–V11 fresh-schema proof | migration proof | PENDING (needs Docker host) |
| 13 | Telemetry | health + Prometheus counters + log levels | live counter values observed | curl | PASS |
