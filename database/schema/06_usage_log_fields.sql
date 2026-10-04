-- ============================================================================
-- 06_usage_log_fields.sql — usage-log dimensions (Module: Usage Log).
-- Authored copy; executable copy is backend/.../db/migration/V7__usage_log_fields.sql.
-- usage_sessions becomes the single usage-log store: source (QR_CHECKIN now,
-- SENSOR/MANUAL later/by staff), lifecycle status, free-text note, and an
-- optional booking (manual entries have none; the unique key still guards
-- one session per booking because Oracle treats NULLs as distinct).
-- ============================================================================

ALTER TABLE usage_sessions ADD (source VARCHAR2(20) DEFAULT 'QR_CHECKIN' NOT NULL);
ALTER TABLE usage_sessions ADD (status VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL);
ALTER TABLE usage_sessions ADD (note VARCHAR2(500));
ALTER TABLE usage_sessions MODIFY (booking_id NULL);
ALTER TABLE usage_sessions ADD CONSTRAINT ck_session_source CHECK (source IN ('QR_CHECKIN', 'SENSOR', 'MANUAL'));
ALTER TABLE usage_sessions ADD CONSTRAINT ck_session_status CHECK (status IN ('ACTIVE', 'COMPLETED'));

-- Backfill for rows predating this module (none in practice, but future-proof).
UPDATE usage_sessions SET status = 'COMPLETED' WHERE ended_at IS NOT NULL;
UPDATE usage_sessions SET source = 'QR_CHECKIN' WHERE source IS NULL;

COMMENT ON COLUMN usage_sessions.source IS 'How the session started: QR_CHECKIN (QR flow), SENSOR (sensor ingest, later), MANUAL (staff-recorded).';
COMMENT ON COLUMN usage_sessions.status IS 'ACTIVE = open, COMPLETED = closed. Historical (COMPLETED) rows are never modified.';
COMMENT ON COLUMN usage_sessions.note IS 'Staff note, used mainly for MANUAL entries.';
