# Demo dataset (synthetic, reproducible)

All names/times are fictional. Recreate it exactly with the steps below (needs a
staff grant for setup calls; `liveuser` is the demo student). This is the dataset
used for the live dashboard verification on 2026-09-28 — every number below was
observed, not invented.

## Recipe (backend on :8080, times UTC)

```bash
B=http://localhost:8080/api/v1
# 1. equipment (staff): OSC-901 Electronics/Lab A, CENT-901 Centrifuges/Lab A,
#    OSC-902 Electronics/Lab B then PUT → MAINTENANCE/OUT_OF_SERVICE
# 2. bookings (student): B1 [now+2h,+4h] → confirm → QR → check-in → checkout
#    (COMPLETED, ~seconds); B2 [now+3h,+5h] → confirm (CONFIRMED);
#    B3 [now+3h,+5h on CENT-901] stays PENDING; B4 overlapping B2 → 409
# 3. manual usage (staff): OSC-901 [now-70m, now-10m] note "walk-in" (3600 s)
# 4. sensor (provision key, then): IN_USE 1.8A, IDLE, OFFLINE, FAULT;
#    then PUT OSC-901 back to AVAILABLE/OPERATIONAL
# 5. overdue: book [now+60s, now+120s] on CENT-901 → confirm → wait for sweep
```

## Observed summary after steps 1–4 (before overdue)

```json
{"equipment":{"total":3,"available":1,"overdue":0,"maintenance":1},
 "bookings":{"total":4,"pending":1,"confirmed":1,"completed":1,"overdue":0},
 "usage":{"totalSeconds":3602,"totalHours":1.0},
 "sensors":{"events":4,"faults":1,"idle":1,"inUse":1,"offline":1,"reliability":1.0},
 "alerts":{"open":0,"resolved":0},"utilizationPercent":0.07,"conflictAttempts":2}
```

Covers: available/heavily-used/low-use items (OSC-901 used 3602 s vs idle items),
bookings in 4 states, a recorded 409 conflict, a completed QR session, a MANUAL
record, 4 sensor events incl. FAULT/OFFLINE, maintenance state, prediction
inputs (3× Monday 10:00 history pattern also verified separately), and audit rows
for every transition. Cleanup: delete alerts → sessions → qr_tokens →
sensor_events → bookings → equipment (children first), revoke temp grants.
