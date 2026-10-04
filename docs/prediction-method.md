# Prediction method (baseline, explainable)

`GET /api/v1/predictions/equipment/{id}` forecasts per-slot availability.
Two methods exist behind the `AvailabilityPredictor` interface
(`?method=empirical|naive`, default `empirical`); a future ML model adds a third
bean — no controller or service changes required.

## Naive baseline (`naive`)

"Manual/current availability": a slot is UNAVAILABLE only with a covering
confirmed booking or an unbookable equipment state, else AVAILABLE with neutral
confidence (0.5). No history is consulted.

## Empirical baseline (`empirical`, default)

Per slot, evaluated in order (all times UTC):

1. **Deterministic layer.** Covering blocking booking (CONFIRMED/CHECKED_IN/OVERDUE)
   or unbookable state (maintenance status ≠ OPERATIONAL, or status MAINTENANCE /
   SENSOR_OFFLINE) → UNAVAILABLE, p = 0, confidence = 1.
2. **Historical layer** over the lookback window (default 28 days = 4 weeks):
   - `busyRate` = weeks with any historical booking (COMPLETED/CONFIRMED/CHECKED_IN)
     overlapping the slot's (day-of-week, hour) block ÷ total weeks.
   - `pBucket = (freeWeeks + 1) / (weeks + 2)` (Laplace smoothing — never 0/1 on
     thin data).
   - `U` = trailing utilization (usage seconds ÷ window capacity).
   - `p = 0.85 × pBucket + 0.15 × (1 − U)`.
   - Status: p ≥ 0.65 AVAILABLE, p ≥ 0.35 LIMITED, else UNAVAILABLE.
   - Confidence = weeks containing any history ÷ total weeks (0.1 with no history
     at all, where p is pinned to neutral 0.5 and the slot is LIMITED).
3. Every slot lists its contributing factors (`existing_booking`, `equipment_state`,
   `dow_hour_occupancy`, `trailing_utilization`, `sample_weeks`,
   `insufficient_history`) — that list IS the explanation.

## Evaluation (`.../evaluation`, staff/admin)

Backtests past slots: each slot is forecast as-of its start (only rows created
before the cutoff inform it) and compared with what actually overlapped it.
Reports slots evaluated, agreement rate, precision/recall for UNAVAILABLE, and
Brier score. Refuses with 400 when history is too thin. This is a consistency
check on recorded data — **no claim about future accuracy is made**, and none
should be quoted from these numbers.

## Limitations (honest)

- Weekly seasonality only; holidays, exams and course schedules are invisible.
- Cold-start items forecast neutral until history accumulates.
- Walk-in usage without bookings/sensors never enters the model.
- Slot buckets use the slot start's (day, hour); multi-hour slots are not
  sub-divided.
