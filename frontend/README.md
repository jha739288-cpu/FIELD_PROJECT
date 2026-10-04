# Frontend — labmarket-frontend

React 18 + React Router 6 + Axios + Vite 5 + TypeScript. No fake data: every page
reads from the Spring Boot API (`VITE_API_BASE_URL`).

## Pages

| Route | Page | Access |
|---|---|---|
| `/` | Landing | public |
| `/login`, `/register` | Auth with client-side validation | public |
| `/dashboard` | Student dashboard (profile + quick actions) | auth |
| `/equipment`, `/equipment/:id` | Catalog list (search/filter/pagination) + details | auth |
| `/book`, `/bookings`, `/calendar` | Booking UI shells — full UI arrives in the booking module | auth |
| `/staff` | Staff dashboard | LAB_STAFF/ADMIN |
| `/admin` | Admin dashboard | ADMIN |

## Structure

```
src/
├── api/        # types, axios client (JWT attach + 401 redirect), auth/equipment services
├── auth/       # AuthContext (token, profile, role helpers, role home)
├── components/ # Layout, ProtectedRoute, StatusBadge, Pagination, Feedback
├── pages/      # route pages (see table)
├── App.tsx     # router · main.tsx — entry · index.css — styles
```

## Run (dev)

Prerequisites: Node 20 LTS, backend running on http://localhost:8080.

```bash
cd frontend
cp .env.example .env   # VITE_API_BASE_URL=http://localhost:8080/api/v1
npm install
npm run dev            # http://localhost:5173
```

 Verify: `npm run typecheck`, `npm run build`.

## Known machine issue (this dev box)

`COMSPEC` is corrupted (`D:\OracleXE213` instead of `C:\Windows\System32\cmd.exe`),
so npm lifecycle scripts fail with `spawn D:\OracleXE213 ENOENT`. Workarounds:

```bash
npm install --ignore-scripts
node node_modules/typescript/bin/tsc --noEmit   # instead of npm run typecheck
node node_modules/vite/bin/vite.js build        # instead of npm run build
node node_modules/vite/bin/vite.js --port 5173  # instead of npm run dev
```

Permanent fix (Windows): set system env `COMSPEC=C:\Windows\System32\cmd.exe`.
