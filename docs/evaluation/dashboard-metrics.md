# Dashboard metrics reference

Every metric below names its **source table/query**, **formula**, **time
interpretation**, **role scope** and **known limitations**. No metric is
hard-coded; no performance numbers are invented.

Conventions: `now` = server time (UTC) at request handling; windows are
half-open `[from, to)`; counts labeled "current" reflect the instant of the
query; counts labeled "windowed" aggregate rows in the window (summary defaults
to trailing 30 days, explicit windows cap at 93 days).

## Equipment (source: `equipment`, point-in-time)

| Metric | Source | Notes |
|---|---|---|
| totalEquipment | `COUNT(*)` | all rows |
| available / reserved / inUse / overdue / maintenance / sensorOffline | `COUNT … GROUP BY currentStatus` (one `countByCurrentStatus` each) | live status |
| operationalCount (utilization denominator) | `COUNT WHERE maintenanceStatus = OPERATIONAL` | current state; see limitation |

Roles: LAB_STAFF, ADMIN (students see the catalog, not analytics).

## Bookings (source: `bookings`)

| Metric | Source | Notes |
|---|---|---|
| total / by status (summary) | `countByStatus` per status, all-time | live snapshot |
| byStatus / byDay / byEquipment (`/bookings`) | one windowed fetch on `createdAt` + UTC grouping in Java | windowed on creation; day buckets are UTC dates |
| cancelled / overdue | status counts (windowed in analytics, all-time in summary) | overdue = ever reached OVERDUE |
| conflictAttempts | `COUNT audit_events WHERE event_type = BOOKING_CONFLICT` in window | written by booking create/confirm on every rejected overlap; 409 responses unchanged |

Roles: LAB_STAFF, ADMIN for lab-wide; students get own counts via `/my-summary`.

## Usage (source: `usage_sessions`)

| Metric | Source | Notes |
|---|---|---|
| sessionsStarted / sessionsCompleted | counts on `startedAt` / `endedAt` in window | |
| totalSeconds / totalHours | merged per-item clipped intervals (see `utilization.md`) | double-count-proof; open sessions clamp at `min(now, to)` |
| utilizationPercent | `100 × mergedSeconds ÷ (operational × window)` | `0.0` when capacity is 0 |
| per-item rows | merged seconds, own percent, `operational` flag | non-operational rows show `0`-basis percent + flag |

Roles: same scoping as bookings.

## Sensors (source: `sensor_events`)

| Metric | Source | Notes |
|---|---|---|
| total / byStatus (IDLE/IN_USE/OFFLINE/FAULT) | `GROUP BY status` in occurred-time window | |
| stale | `occurredAt` older than 5 min at ingest (flagged, still stored) | |
| faults / offline | status counts | faults = FAULT reports (device-declared problems) |
| reliability | `(total − stale) / total`, `null` when total = 0 | **freshness**, not hardware MTBF — see limitation |
| byEquipment | `GROUP BY equipmentCode` in window | |

Limitations (explicit, not hidden):
- Rejected payloads (bad key, bad values, absurd timestamps) are **never stored**,
  so they cannot appear in any ratio — only application logs see them.
- "Reliability" therefore measures *data freshness*, not device health. A sensor
  that reports FAULT faithfully scores reliable; that is intended.
- Summary windows on *receipt* time (`createdAt`); analytics windows on
  *occurrence* time (`occurredAt`). Both are labelled in code.

Roles: LAB_STAFF, ADMIN.

## Alerts (source: `alerts`)

| Metric | Source | Notes |
|---|---|---|
| openOverdueAlerts / resolvedOverdueAlerts | `COUNT WHERE type = OVERDUE AND status = …` | exactly one OPEN alert per booking by construction |

Roles: LAB_STAFF, ADMIN.

## Date/time interpretation

- All timestamps UTC (`TIMESTAMPTZ` in Oracle, ISO-8601 on the wire).
- Booking/session math uses half-open intervals; back-to-back ranges share no time.
- Booking times are millis-normalized at write (see booking module docs).

## Limitations summary

1. Maintenance exclusion uses *current* state (no historical maintenance log yet).
2. Rejected sensor payloads are uncounted (never persisted).
3. Reliability = freshness, not hardware reliability.
4. Day buckets are UTC, not lab-local time.
5. Utilization ignores staffing/opening hours (no such model) — denominator is
   wall-clock window × operational fleet, stated on every response.
