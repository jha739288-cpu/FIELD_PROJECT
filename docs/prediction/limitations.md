# Limitations — read before quoting any number

1. **This is not machine learning.** No model is trained, fitted, or validated
   on a hold-out set. It is an explainable counting baseline. Calling it "AI"
   in a report or demo would be dishonest.
2. **Predictions are advisory.** Actual bookings, maintenance and live status
   always take precedence — enforced in code (deterministic layer runs first)
   and stated on every response (`disclaimer`) and screen.
3. **Weekly seasonality only.** Holidays, exam periods, course timetables and
   supervisor habits are invisible to the model.
4. **Cold start is neutral by design.** New items forecast LIMITED / 0.5 /
   confidence 0.1 until history accumulates. That is the documented
   insufficient-data state, not a bug.
5. **Walk-ins are invisible.** Usage without a booking or sensor record never
   enters any feature.
6. **Slot buckets use the slot start's (day, hour).** Multi-hour slots are not
   sub-divided; a 4-hour slot crossing lunch uses its start hour's pattern.
7. **Maintenance has no schedule.** Only the *current* state is known, so a
   future maintenance shutdown cannot be foreseen — the slot will read as
   available until staff flag the item.
8. **Evaluation scores are self-consistency, not future accuracy.** See
   `evaluation.md §6`. Do not present backtest numbers as expected live performance.
9. **UTC everywhere.** Day/hour buckets use UTC; local-time behaviour near
   midnight may surprise users in other zones (documented, not converted).

## Future ML extension (when data justifies it)

1. Accumulate ≥ one full term of bookings + usage (the features already exist).
2. Implement `AvailabilityPredictor` (e.g. logistic regression on
   day-of-week × hour + trailing load) as a new `@Component("ml-…")` bean.
3. Select it per request with `?method=ml-…`; compare against `empirical`
   with `/evaluation` on identical windows before switching the default.
4. Only then — with a trained, held-out-validated model — may reports use the
   term "machine learning".
