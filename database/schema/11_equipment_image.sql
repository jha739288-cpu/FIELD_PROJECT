-- ============================================================================
-- 11_equipment_image.sql — optional photo URL for marketplace cards (UI upgrade).
-- Authored copy; executable copy is backend/.../db/migration/V12__equipment_image.sql.
-- Nullable and backward compatible: existing rows are untouched (NULL = monogram
-- fallback in the UI). Needed because the marketplace must show equipment photos
-- and no image field exists.
-- ============================================================================

ALTER TABLE equipment ADD (image_url VARCHAR2(500));

COMMENT ON COLUMN equipment.image_url IS 'Optional photo URL (object storage / CDN). NULL means no photo.';
