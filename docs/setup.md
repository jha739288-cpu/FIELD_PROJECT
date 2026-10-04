# Setup (Windows + clean-machine reproducible)

## 1. Install (one time)

| Tool | Version | Link / command |
|---|---|---|
| JDK | 21 (Temurin or Oracle) | `winget install EclipseAdoptium.Temurin.21.JDK` |
| Maven | 3.9+ | `winget install Apache.Maven` |
| Node.js | 20 LTS | `winget install OpenJS.NodeJS.LTS` |
| Docker Desktop | latest (for `docker compose`) | `winget install Docker.DockerDesktop` |
| Git | latest | `winget install Git.Git` |
| Oracle XE 21c | only for non-Docker runs | Oracle installer, then create the schema owner (backend README) |
| Python 3 | only for the sensor simulator | `winget install Python.Python.3` |

Verify:

```powershell
java -version
mvn -version
node -v; npm -v
docker --version; docker compose version
```

## 2. Configure

```powershell
Copy-Item .env.example .env
# then edit .env: set ORACLE_PASSWORD, DB_PASSWORD and JWT_SECRET (min 32 chars)
Copy-Item frontend\.env.example frontend\.env
```

## 3. Run (full stack via Docker, recommended)

```powershell
docker compose build
docker compose up -d
```

- Frontend http://localhost:5173
- API http://localhost:8080/api/v1, Swagger http://localhost:8080/swagger-ui.html
- Health http://localhost:8080/api/v1/health, actuator http://localhost:8080/actuator/health
- Oracle XE localhost:1521 (service `XEPDB1`)

Full procedure + troubleshooting: `docs/deployment/docker.md`.

## 4. Run backend without Docker (local Oracle XE running)

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DB_URL="jdbc:oracle:thin:@localhost:1521/XEPDB1"
$env:DB_USERNAME="labmarket"
$env:DB_PASSWORD="change-me-in-dev"
$env:JWT_SECRET="change-me-to-a-256-bit-secret-min-32-chars-long-xxxx"
mvn spring-boot:run
```

## 5. Run frontend without Docker

```powershell
cd frontend
npm install
npm run dev
```
