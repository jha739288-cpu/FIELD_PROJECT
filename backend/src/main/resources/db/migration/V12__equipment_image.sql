-- ============================================================================
-- Flyway V12 — executable copy of database/schema/11_equipment_image.sql.
-- Do not edit after it has been applied anywhere; add a new V13_... instead.
-- ============================================================================

ALTER TABLE equipment ADD (image_url VARCHAR2(500));

COMMENT ON COLUMN equipment.image_url IS 'Optional photo URL (object storage / CDN). NULL means no photo.';
