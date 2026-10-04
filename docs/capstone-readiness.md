# Capstone readiness vs project mandate

COMPLETE = evidence exists in repo/test logs. PENDING = externally blocked.
NOT STARTED = no artifact yet. Nothing is marked complete without evidence.

## Implementation (all COMPLETE, evidence: 210 tests + live Oracle runs)

industry problem · modular architecture · secure APIs · automated tests ·
equipment catalog · booking calendar · QR check-in · current-status sensor ·
usage log · overdue alert · utilization dashboard · predictive availability
(baseline + evaluation) · database design (Flyway V1–V11) · API contracts
(Swagger + `docs/api-standards.md`) · threat-aware design (security phase) ·
test strategy (`docs/evaluation/*`) · failure experiment (robustness phase:
races, replays, floods, reruns) · security experiment (16-check matrix) ·
telemetry (Actuator/Prometheus + docs) · Docker config · CI config ·
evaluation (dashboard metrics, prediction backtest with real numbers).

## Academic artifacts

- SRS · personas · stakeholder validation · misuse cases · measurable success
  criteria · UI prototype · threat model document · user satisfaction study:
  **NOT STARTED** (no such files exist; treat as report-phase work).
- Administrator guide · user guide: **NOT STARTED** (README + module READMEs
  cover operations; end-user guides unwritten).
- Demo video · presentation · final report: **NOT STARTED** (explicitly deferred).
- Individual contribution evidence: **PENDING** (requires git history + PRs —
  see `docs/engineering-evidence/`).

## Deployment evidence

- Dockerfiles/compose: COMPLETE as configuration (YAML-validated).
- `docker compose up` execution: **PENDING** (no Docker daemon on this box).
- GitHub Actions execution: **PENDING** (no repo yet).
- Oracle migration proof: COMPLETE (fresh-schema V1→V11 boot verified live).
