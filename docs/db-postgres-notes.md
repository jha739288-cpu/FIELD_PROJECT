# PostgreSQL notes (why not Oracle)

Per your instruction the project targets **PostgreSQL 16**, not Oracle.

| Concern | Oracle approach (NOT used) | PostgreSQL approach (used) |
|---|---|---|
| Driver | `ojdbc11` | `org.postgresql:postgresql` |
| PK type | `NUMBER GENERATED AS IDENTITY` | `UUID DEFAULT gen_random_uuid()` (`pgcrypto`) |
| Timestamps | `TIMESTAMP WITH TIME ZONE` (similar) | `TIMESTAMPTZ` everywhere, UTC ISO-8601 |
| No-overlap bookings | trigger / app check only | `EXCLUDE USING gist (equipment_id WITH =, tstzrange(start,end) WITH &&)` **plus** service check → `409` |
| Case-insensitive search | `UPPER()` | `CITEXT` / `ILIKE` + `pg_trgm` |
| Schema versions | Liquibase/Flyway both fine | Flyway `V*.sql` in `backend/src/main/resources/db/migration` |
| JSON payloads | `CLOB`/`JSON` | `JSONB` (sensor events) |

`docker-compose.yml` runs `postgres:16-alpine`; `database/init/01-extensions.sql`
enables `pgcrypto`, `citext`, `pg_trgm`, `btree_gist` on first volume creation.
