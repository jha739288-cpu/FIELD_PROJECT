-- ============================================================================
-- Flyway V11 — executable copy of database/schema/10_usage_indexes.sql.
-- Do not edit after it has been applied anywhere; add a new V12_... instead.
-- ============================================================================

CREATE INDEX ix_usage_equipment_started ON usage_sessions (equipment_id, started_at);
CREATE INDEX ix_usage_user ON usage_sessions (user_id);
