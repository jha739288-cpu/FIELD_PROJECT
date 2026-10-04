# Troubleshooting (operations)

## Health / metrics

- `/actuator/health` DOWN on `db` → Oracle unreachable: check container health,
  `DB_URL` host (`oracle` in compose, `localhost` locally), credentials, firewall.
- `/actuator/prometheus` 401/403 → expected: metrics need a JWT (only
  health/info are public besides the API). Login first, then
  `curl -H "Authorization: Bearer $T" .../actuator/prometheus`.
- Custom counters absent from Prometheus output → they export on first
  increment (zero-valued counters are not pre-registered); trigger one event
  (e.g. a booking conflict) and re-check.

## Investigating sensor failure

1. `GET /api/v1/sensors/events?equipmentCode=X` (staff): latest `status`,
   `stale` flags, `occurredAt` vs `receivedAt` gaps (clock drift).
2. Logs: `WARN` lines for stale/contradictory events name the equipment code.
3. `sensor_failures_total` increasing without `sensor_events_total` → bad keys
   or broken clocks (future/ancient rejections); check provisioning
   (`POST /equipment/{id}/sensor-key` rotates).
4. `OFFLINE` streak with no rows at all → device/radio dead (absence of data
   is itself the signal); check power/network, then the overdue module's view.

## Investigating booking conflicts

1. `increase(booking_conflicts_total[1h])` shows the rate; each rejection also
   writes a `BOOKING_CONFLICT` audit row naming equipment + window.
2. `GET /api/v1/bookings/calendar?from&to&equipmentId=` shows who holds the slot.
3. Bursts from one user + 409s = double-submit or genuine contention; the
   equipment row lock (`FOR UPDATE`) serializes racers — proven by
   `BookingConcurrencyTest`.

## Investigating overdue processing

1. Scheduler evidence: `Overdue sweep finished: N newly overdue booking(s)` in
   logs every `OVERDUE_CHECK_INTERVAL_MS` (default 5 min).
2. `overdue.detected_total` vs `alerts` table: exactly one OPEN alert per
   booking (dedupe by status + existence check); reruns return 0.
3. Stuck OVERDUE booking → resolve via `PUT /api/v1/alerts/{id}/resolve`
   (staff); never UPDATE rows by hand (would bypass session closing + audits).

## Investigating prediction errors

1. 400s: reversed window, >93 days, bad `slotMinutes` (15–480), unknown
   `?method=`, unknown id → 404.
2. Suspicious probabilities: fetch `?method=naive` for the same window — if
   naive looks sane, the empirical inputs (history sparseness, maintenance
   state) are the cause; check `confidence` and `factors` on each slot.
3. Backtest confusion: evaluation is as-of-cutoff consistency, and booking
   status mutation weakens its deterministic layer (documented in
   `docs/prediction/evaluation.md`) — re-check against the recorded caveat
   before calling it a bug.
