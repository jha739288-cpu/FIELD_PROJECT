# Predictive Availability

Forecasts how likely each equipment item is to be free in a future slot —
an **explainable baseline**, explicitly not machine learning (no trained model
exists in this project; see `limitations.md`).

- User question answered: *"How likely is this equipment to be available
  during a future time slot?"*
- API: `GET /api/v1/predictions/equipment/{id}?from=&to=&slotMinutes=&method=`
  (any authenticated user), `.../evaluation` (staff/admin).
- UI: *Predict Availability* from Equipment Details; advisory strip, never a
  booking control.

Start here, then read:

1. `methodology.md` — approach, features, formula, states.
2. `evaluation.md` — backtesting, metrics, leakage prevention, how to run it.
3. `limitations.md` — what the numbers are not, and the ML extension path.

Honest vocabulary used everywhere (code, API, UI, docs): **predicted** availability
is advisory; **actual** availability (bookings, maintenance, live status) always wins.
