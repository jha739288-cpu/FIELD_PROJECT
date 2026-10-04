-- ============================================================================
-- 10_usage_indexes.sql — analytics support (Module: Dashboard).
-- Authored copy; executable copy is backend/.../db/migration/V11__usage_indexes.sql.
-- Justification: utilization/usage analytics scan sessions by equipment and time,
-- and personal history filters by user. usage_sessions previously had no
-- indexes beyond its PK/unique key; these two cover the new access paths.
-- Idempotent by migration versioning (Flyway applies each version exactly once).
-- ============================================================================

CREATE INDEX ix_usage_equipment_started ON usage_sessions (equipment_id, started_at);
CREATE INDEX ix_usage_user ON usage_sessions (user_id);
