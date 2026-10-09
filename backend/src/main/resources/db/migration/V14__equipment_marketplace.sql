-- ============================================================================
-- Flyway V14 — executable copy of database/schema/13_equipment_marketplace.sql.
-- Do not edit after it has been applied anywhere; add a new V15_... instead.
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
