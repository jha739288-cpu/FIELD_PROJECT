-- ============================================================================
-- 13_equipment_marketplace.sql — vendor marketplace fields (Role marketplace).
-- Authored copy; executable copy is backend/.../db/migration/V14__equipment_marketplace.sql.
-- All nullable/backward compatible: existing rows are untouched (NULL = not
-- specified). Needed because the vendor "Add Equipment" form requires
-- specifications, pricing, quantity and usage/safety notes, none of which exist.
-- ============================================================================

ALTER TABLE equipment ADD (specifications VARCHAR2(2000));
ALTER TABLE equipment ADD (price_per_hour NUMBER(10, 2));
ALTER TABLE equipment ADD (quantity NUMBER DEFAULT 1 NOT NULL);
ALTER TABLE equipment ADD (usage_instructions VARCHAR2(1000));
ALTER TABLE equipment ADD (safety_info VARCHAR2(1000));

COMMENT ON COLUMN equipment.specifications IS 'Free-text technical specifications.';
COMMENT ON COLUMN equipment.price_per_hour IS 'Booking price per hour in platform currency. NULL = contact vendor.';
COMMENT ON COLUMN equipment.quantity IS 'Identical units available at the location.';
COMMENT ON COLUMN equipment.usage_instructions IS 'How to operate the instrument.';
COMMENT ON COLUMN equipment.safety_info IS 'Safety precautions for operators.';
