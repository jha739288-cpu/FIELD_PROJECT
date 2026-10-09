-- ============================================================================
-- 12_role_model.sql — rename application roles STUDENT→USER, LAB_STAFF→VENDOR.
-- Authored copy; executable copy is backend/.../db/migration/V13__role_model.sql.
-- Existing accounts keep their memberships; only the role names change.
-- ADMIN is unchanged. A repeatable seed (R__seed_roles) is updated separately
-- to insert the new names idempotently.
-- ============================================================================

-- The old check must be dropped BEFORE renaming: Oracle validates CHECK
-- constraints on UPDATE, so renaming under the old check would fail.
ALTER TABLE roles DROP CONSTRAINT ck_roles_name;

UPDATE roles SET name = 'USER' WHERE name = 'STUDENT';
UPDATE roles SET name = 'VENDOR' WHERE name = 'LAB_STAFF';

ALTER TABLE roles ADD CONSTRAINT ck_roles_name CHECK (name IN ('ADMIN', 'USER', 'VENDOR'));

COMMENT ON COLUMN roles.name IS 'One of ADMIN, USER, VENDOR (enforced by ck_roles_name).';
