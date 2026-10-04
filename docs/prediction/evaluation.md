# Evaluation — backtesting the baseline

## 1. What is measured (and why these metrics)

Each past slot is forecast **as-of its start** and compared with what actually
overlapped it. Reported per evaluation run:

| Metric | Formula | Why this one |
|---|---|---|
| Agreement (accuracy) | correct slots ÷ slots | Overall consistency, easy to explain. |
| Precision (UNAVAILABLE) | true-blocked ÷ predicted-blocked | "When we warn, are we right?" — the operationally costly error is a false alarm that turns students away. |
| Recall (UNAVAILABLE) | true-blocked ÷ actually-blocked | "How many busy slots did we flag in advance?" — missed warnings waste trips to the lab. |
| Brier score | mean((p − actual)²), actual ∈ {0,1} | The only listed metric that scores *probabilities*, not just labels — required because the model outputs P(available). Lower is better; 0.25 = always guessing 0.5. |

Accuracy alone would be misleading (mostly-free fleets score high by saying
"free" always); precision/recall expose that, and Brier keeps probabilities honest.

## 2. Dataset

The lab's own rows: bookings (COMPLETED/CONFIRMED/CHECKED_IN) and usage sessions
in `[from − lookback, to)`. The endpoint refuses with 400 when there is no
history at all — an evaluation on nothing would be fabrication.

## 3. Procedure (reproducible)

```
GET /api/v1/predictions/equipment/{id}/evaluation?from=&to=&slotMinutes=
```

1. Pick a past window fully covered by recorded history.
2. For each slot: cutoff = slot start; forecast using only rows with
   `created_at ≤ cutoff` and starts before the cutoff (code: `PredictionService.evaluate`).
3. Actual outcome = any historical booking overlapping the slot.
4. Aggregate the four metrics above.

## 4. Data-leakage prevention (explicit)

- Future bookings/usage can never leak: every slot is forecast with data filtered
  to `created_at ≤ slot start` and ranges clipped to the cutoff.
- `createdAt` is set once by `@PrePersist` and never updated, so the cutoff is
  tamper-evident.
- Rows created after the cutoff (including backdated *bookings* whose record is
  new) are excluded from features — only their overlap counts as ground truth.

## 5. Baseline comparison

Run the same window twice: `?method=` only affects `/predictions`, while
`/evaluation` always backtests the empirical model; compare its agreement/Brier
against the naive model's implied behaviour (naive = current-state only, so its
backtest equals "predict free unless a booking already covered the slot",
computable from the same response data). Report both; do not quote either as a
future-accuracy claim.

## 6. Limitations

- Consistency check on recorded data, not a forecast-accuracy guarantee.
- Sparse fleets produce wide, low-confidence numbers — correct behaviour, not a bug.
- No held-out future test is possible until the system runs long enough to have
  one; until then, treat all scores as *model self-consistency*, nothing more.
- **No measured results are recorded here.** Run the endpoint and paste the
  response if a report needs numbers — never invent them.
