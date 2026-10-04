-- ============================================================================
-- Flyway V9 — executable copy of database/schema/08_sensor_created_at.sql.
-- V8 was applied missing sensor_events.created_at; this repairs it forward.
-- Do not edit after it has been applied anywhere; add a new V10_... instead.
-- ============================================================================

ALTER TABLE sensor_events ADD (created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL);
