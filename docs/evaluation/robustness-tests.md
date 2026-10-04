# Robustness experiments (measured)

Backend suite: **210/210 green** (includes the tests below). Live runs against
Oracle XE on 2026-10-04 unless noted.

## A. Duplicate booking

- Setup: item SEC-001, confirmed booking B1 = [T+2h, T+4h].
- Input: second booking B2 = [T+3h, T+5h] same item.
- Expected: 409 with overlap message + `BOOKING_CONFLICT` audit, B1 untouched.
- Actual: 409 + audit row (verified live and in `BookingControllerTest`).
- Conclusion: conflict detection rejects duplicates; audit records the attempt.

## B. Invalid QR

- Inputs: unknown token → 404; used token → 409; expired token → 410 (tested,
  `QrCheckInControllerTest`); wrong user → 403; too-early/too-late/cancelled →
  409. Every rejection writes `QR_VALIDATION_FAILED` in its own transaction.
- Actual (live): 404/409/403 observed as expected; audit rows
  (`QR_VALIDATION_FAILED` ×4 in one session) confirmed in-table.
- Conclusion: untrusted QR input fails safely with precise codes and a trail.

## C. Unauthorized API

- See `security-tests.md` rows 1–7: 401/403 split holds live on every layer
  (filter entry point, `@PreAuthorize`, service owner-checks).
- Conclusion: frontend role UI is backed by backend enforcement everywhere probed.

## D. Sensor failure

- Inputs: OFFLINE → `SENSOR_OFFLINE`; FAULT → `MAINTENANCE`; `MAINTENANCE` item
  ignores later IDLE (verified live); negative current → 400; future/ancient
  timestamps → 400; identical payload twice → stored twice (201/201, documented
  observation semantics, not merged).
- Abuse: 10 rapid events → all 201 (under the 240/min/key + global caps);
  the limiter itself is unit-tested (burst/block/reset/buckets).
- Conclusion: fail-safe mapping holds; floods are capped per key and globally.

## E. Invalid sensor event

- Covered by D plus: unknown code → 401 (no oracle), bad enum → 400, missing
  timestamp → 400 (`SensorEventControllerTest`, 11 tests green).
- Conclusion: hostile input is rejected before any state change.

## F. Overdue scheduler repeat

- Evidence: `OverdueScheduledJobTest` (delegation), `OverdueServiceTest`
  (idempotent rerun), `OverdueControllerTest.rerunIsIdempotent` (second sweep
  returns 0, still exactly 1 alert + 1 audit). Live logs show repeated
  `sweep finished: 0 newly overdue` runs with no duplicate alerts.
- Resolve-twice → 409; resolve-missing → 404 (tested).
- Conclusion: repeats are no-ops; resolution is single-shot.

## G. Invalid prediction request

- Live: unknown id → 404; reversed window → 400; `slotMinutes=5` → 400;
  unknown `?method=` → 400; fresh item → LIMITED/0.5/conf 0.1 with
  `insufficient_history`; maintenance item → UNAVAILABLE/`equipment_state`;
  covering booking → UNAVAILABLE p=0. All probabilities observed within [0, 1].
- Conclusion: invalid input rejected; degenerate states are explicit, not silent.

## H. Database/API failure

- FK protection: deleting equipment with bookings → 409 via explicit domain
  check (`existsByEquipmentId`) + `DataIntegrityViolationException` backstop
  (regression-tested; previously 500 on databases without the FK path).
- No cascade deletes anywhere: all associations are `@ManyToOne` without
  cascade; `audit_events` has no incoming references and no update/delete API,
  so history cannot be destroyed through the API.
- Orphan check: every child row (bookings, sessions, tokens, events, alerts)
  carries a non-nullable FK except `usage_sessions.booking_id` (nullable by
  design for MANUAL entries); manual cleanup scripts delete children before
  parents.
- Conclusion: destructive operations fail closed; audit history is protected.
