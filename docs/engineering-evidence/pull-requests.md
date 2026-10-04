# PR checklist (to be created genuinely on GitHub — none exist yet)

One pull request per completed phase, each with: linked issue, description of
what/why, test evidence, and a reviewer approval. Suggested (not mandatory) split:

- PR 1 — Project foundation (monorepo, configs, Oracle/Flyway baseline)
- PR 2 — Authentication and equipment catalog
- PR 3 — Booking, conflict protection, calendar, QR workflow
- PR 4 — Usage logging, sensor subsystem, overdue + alerts
- PR 5 — Dashboard, analytics, predictive availability + evaluation
- PR 6 — Security/robustness hardening (rate limits, handlers, concurrency proof)
- PR 7 — Docker, CI, telemetry, operations docs

Merge checklist per PR: CI green (4 jobs) · no secrets added (`git ls-files`
check) · docs updated · reviewer sign-off. Do NOT fabricate any of these.
