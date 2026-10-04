-- ============================================================================
-- 08_sensor_created_at.sql — repair for 07_sensor.sql, whose executable copy
-- (V8) was applied missing sensor_events.created_at. New migration instead of
-- editing the applied V8, per the project convention.
-- ============================================================================

ALTER TABLE sensor_events ADD (created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL);
