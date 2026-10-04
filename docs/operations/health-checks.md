# Health checks

Layered, cheapest-first. All commands assume the compose ports
(5173 web, 8080 api, 1521 oracle); adjust for local runs.

## 1. Oracle

```bash
docker compose ps oracle                       # healthy (healthcheck: SELECT 1 FROM DUAL)
# without Docker:
echo "SELECT 1 FROM DUAL;" | sqlplus -S labmarket/<pw>@localhost:1521/XEPDB1
```

Unhealthy → see `troubleshooting.md` (RAM, first-boot time, `ORA-01017`).

## 2. Backend

```bash
curl http://localhost:8080/api/v1/health        # public: {"status":"UP",...}
curl http://localhost:8080/actuator/health      # public groups; DOWN here means DB is down
```

With a token, `GET /actuator/prometheus` must contain `booking_conflicts_total`,
`overdue_detected_total`, `sensor_events_total`, `prediction_requests_total`
(zero-valued until first use — Prometheus exports them on first increment;
`process_uptime_seconds` and `http_server_requests_seconds_count` are immediate).

Backend UP + actuator DOWN on `db`/`oracle` component = connection failure, not
an app bug — check `DB_URL`/credentials/network before anything else.

## 3. Frontend

```bash
curl -o /dev/null -w "%{http_code}\n" http://localhost:5173/          # 200
curl -o /dev/null -w "%{http_code}\n" http://localhost:5173/equipment # 200 (SPA fallback)
```

A served page with API errors means the browser cannot reach the backend:
verify `FRONTEND_API_URL` baked at image build time and `CORS_ALLOWED_ORIGINS`.
