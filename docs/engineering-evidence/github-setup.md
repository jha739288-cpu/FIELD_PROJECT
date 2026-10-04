# GitHub setup (exact manual steps)

Nothing here has been executed yet — no `.git/`, no remote, no runs. Do the
steps below on a machine with Git + (for Docker/CI) Docker Desktop.

## 1. Create the repository

```bash
cd "FIELD PROJECT"
git init
git add .
git commit -m "LabMarket capstone: full application (backend 210 tests green)"
git branch -M main
```

On github.com: New repository → name (e.g. `labmarket`) → visibility decision:
**private** if student IDs/grades appear anywhere later, else public → *do not*
add README/license via the UI (the tree already has them). Then:

```bash
git remote add origin <GITHUB_REPOSITORY_URL>   # paste the real URL; none is invented here
git push -u origin main
```

Verify `.env` is NOT tracked: `git ls-files | grep -E '(^|/)\.env$'` must print nothing.

## 2. Actions enablement

Settings → Actions → General → allow all actions. The `ci` workflow runs on the
next push/PR to `main` automatically.

## 3. Branch protection (`main`)

Settings → Branches → Add rule for `main`: require pull request before merging,
require status checks (`backend-test`, `frontend`, `oracle-migration`,
`docker-build`), dismiss stale approvals.

## 4. Branch strategy (student-friendly)

- `main` = stable, always deployable; merged only via reviewed PRs.
- `develop` = integration branch for ongoing work.
- `feature/<topic>` = individual work, branched from `develop`, PR back into it.

```bash
git checkout -b develop && git push -u origin develop
git checkout -b feature/<topic>   # work, commit, push, open PR to develop
```

## 5. Required status checks

In the protection rule, tick all four CI jobs. A PR cannot merge red.

## 6. Issues (create these first)

`setup`: repo hygiene · `docker-first-run`: execute compose on a Docker host ·
`ci-first-green`: record first Actions run · `python-env`: install Python 3 for
the simulator · `thesis-writeup`: final report · `demo-video`: recording.

## 7. Pull requests (genuine, created after push — see PR checklist doc section in
`docs/engineering-evidence.md`)

One PR per phase (foundation/auth/booking/QR+usage/sensor+overdue/dashboard+
prediction/quality/docker+CI). Each PR: linked issue, what/why, test evidence
(`mvn` counts, screenshots of green runs), reviewer approval before merge.
