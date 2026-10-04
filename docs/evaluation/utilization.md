# Utilization definition

All utilization numbers in LabMarket come from **stored rows only** — no estimates,
no sampling, no hard-coded values. This note pins down the exact formula so a
reader can reproduce every percentage from the database.

## Formula

```
utilization % = 100 × merged_usage_seconds ÷ (operational_items × window_seconds)
```

- **Numerator** (`merged_usage_seconds`): for each equipment item, take its usage
  sessions touching the window `[from, to)`, clip each to the window, **merge
  overlapping intervals per item** (shared time counts once), and sum. Sessions
  of different items always add up — two instruments used at once is two hours
  of usage, not one.
- **Denominator**: `window_seconds × operational_items`, where `operational_items`
  counts equipment whose `maintenance_status` is `OPERATIONAL` **right now**.
- Result is rounded to 2 decimals. A per-item percentage uses the same formula
  with that item's merged seconds over the full window.

Implemented in `DashboardService` (`mergedByEquipment`, `utilization`,
`equipmentDashboard`, `summary`).

## Operating-window definition

There is no fixed "lab opens 9–5" schedule in the data model, so the window is
explicit on every call: `from`/`to` query parameters (ISO-8601, UTC), defaulting
to the trailing 30 days ending now, capped at 93 days. The response echoes the
effective window, so every number is auditable.

## Edge cases

| Case | Rule |
|---|---|
| Zero available time (window length 0, or no operational items) | `0.0` — never divide by zero, never null |
| No usage records | `0.0` |
| Overlapping sessions, same item (e.g. QR + MANUAL overlap) | merged first — counted once |
| Open (ACTIVE) sessions | counted up to `min(now, to)` — real ongoing usage, labelled by session status |
| Sessions fully outside the window | contribute `0` (clipped away) |
| Items under maintenance (`maintenanceStatus != OPERATIONAL`) | excluded from capacity AND totals; still listed per-item with `operational: false` |
| Historical maintenance periods | **limitation**: only the *current* maintenance state is known, so exclusion uses present state (documented, not hidden) |
| Back-to-back sessions | half-open intervals share no time — no special-casing needed |

## Worked example

Window 2026-09-24T00:00Z → 2026-09-25T00:00Z (86 400 s), 2 operational items.
Item A used [10:00, 11:00] → 3 600 s. Item B unused.

```
utilization % = 100 × 3600 ÷ (2 × 86400) = 2.08 %
```

Item A alone: `100 × 3600 ÷ 86400 = 4.17 %`. These exact numbers are asserted in
`DashboardControllerTest.utilizationMathIsExact`.

## What utilization is NOT

- Not a booking ratio (reserved-but-unused time counts as idle).
- Not availability (see `/availability`, which answers "can I book now?").
- Not predictive (see `docs/prediction-method.md` for forecasts).
