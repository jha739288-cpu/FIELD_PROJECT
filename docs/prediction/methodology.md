# Methodology — empirical baseline predictor

Method key `empirical` (default). Pure function of its inputs
(`AvailabilityPredictor.predict`), so it is unit-testable and backtestable.
A future ML model implements the same interface under a new `?method=` name —
no controller/service changes needed.

## 1. Problem

For equipment item *e* and future half-open slot *[s, e)*, estimate
P(available) ∈ [0, 1] with reasons a student can understand.

## 2. Input data (all already in the database — no new tables)

- **Future blocking bookings** (CONFIRMED / CHECKED_IN / OVERDUE overlapping
  the window) — deterministic layer.
- **Historical bookings** (COMPLETED / CONFIRMED / CHECKED_IN) in the trailing
  lookback (default 28 days = 4 weeks, `app.prediction.lookback-days`).
- **Historical usage sessions** in the same lookback.
- **Current equipment state** (`currentStatus`, `maintenanceStatus`).

Deliberately NOT used: booking purposes, usernames, sensor amperage, audit
trails. They add no availability signal worth their privacy/complexity cost.

## 3. Features (only reliable, available ones)

| Feature | Definition |
|---|---|
| `existing_booking` | Any blocking future booking covers the slot → UNAVAILABLE, p = 0, conf = 1. Booking state always wins. |
| `equipment_state` | Item not bookable right now (maintenance ≠ OPERATIONAL, or status MAINTENANCE / SENSOR_OFFLINE) → UNAVAILABLE, p = 0, conf = 1. Maintenance is a fact, not a forecast. |
| `dow_hour_occupancy` | `busyRate` = weeks (of 4) with any historical booking overlapping the slot's (day-of-week, hour) block ÷ 4. Weekly seasonality is the only pattern the data can support. |
| `trailing_utilization` | Usage seconds ÷ lookback capacity (see `docs/evaluation/utilization.md`). High load drags availability down. |
| `sample_weeks` | Weeks containing any history — drives confidence, not probability. |

## 4. Formula

```
pBucket = (freeWeeks + 1) / (weeks + 2)        # Laplace smoothing: never 0/1 on thin data
p       = 0.85 × pBucket + 0.15 × (1 − U)      # documented linear blend
p       = clamp(p, 0, 1)
status  = p ≥ 0.65 → AVAILABLE | p ≥ 0.35 → LIMITED | else UNAVAILABLE
confidence = weeksWithData / weeks             # 0.1 floor
```

`availability_probability = 1 − estimated P(in use)`: `pBucket` estimates the
free-slot rate, `(1 − U)` the unloaded-capacity rate. Every slot returns its
contributing factors (`existing_booking`, `equipment_state`, `dow_hour_occupancy`,
`trailing_utilization`, `sample_weeks`, `insufficient_history`) — that list IS
the explanation shown in API and UI.

## 5. Insufficient data (project convention)

There is no history → the slot is **LIMITED, p = 0.5, confidence = 0.1** with an
`insufficient_history` factor, rendered in the UI as *INSUFFICIENT DATA — neutral
forecast, very low confidence*. Rationale (documented choice, not oversight): a
null probability would break the Brier-score evaluation and all frontend math;
a pinned neutral forecast with minimum confidence is computable and honest.
Minimum data for a real (non-neutral) forecast: ≥ 1 historical booking or session
in the lookback window.

## 6. Maintenance vs predicted-low (kept distinct)

- Unbookable state → status UNAVAILABLE, factor `equipment_state` naming the
  exact state (UI renders *"UNAVAILABLE — Maintenance"*). A fact, conf = 1.
- Low historical score → LIMITED/UNAVAILABLE with `dow_hour_occupancy` factors
  and confidence < 1. A forecast. Never confused with each other.

## 7. Naive baseline (`naive`, `?method=naive`)

"Manual/current availability": covering booking or unbookable state →
UNAVAILABLE (p = 0, conf = 1); otherwise AVAILABLE (p = 1, conf = 0.5). No
history consulted. Exists so the empirical model can be compared against
doing-nothing-smart in the evaluation.
