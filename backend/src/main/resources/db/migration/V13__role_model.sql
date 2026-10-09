-- ============================================================================
-- Flyway V13 — executable copy of database/schema/12_role_model.sql.
-- Do not edit after it has been applied anywhere; add a new V14__... instead.
-- ============================================================================

-- The old check must be dropped BEFORE renaming: Oracle validates CHECK
-- constraints on UPDATE, so renaming under the old check would fail.
ALTER TABLE roles DROP CONSTRAINT ck_roles_name;

UPDATE roles SET name = 'USER' WHERE name = 'STUDENT';
UPDATE roles SET name = 'VENDOR' WHERE name = 'LAB_STAFF';

ALTER TABLE roles ADD CONSTRAINT ck_roles_name CHECK (name IN ('ADMIN', 'USER', 'VENDOR'));

COMMENT ON COLUMN roles.name IS 'One of ADMIN, USER, VENDOR (enforced by ck_roles_name).';
