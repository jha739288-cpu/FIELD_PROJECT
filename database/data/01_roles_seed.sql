-- ============================================================================
-- 01_roles_seed.sql — reference data: the three application roles.
-- Idempotent MERGE: safe to re-run. Applied automatically by the backend as
-- a Flyway repeatable migration (R__seed_roles.sql). NEVER put user accounts
-- or password material in seed files — users are created via registration
-- (Module 1) with BCrypt-hashed passwords.
-- ============================================================================

MERGE INTO roles r
USING (
  SELECT 'USER'   AS name, 'User — book and use equipment'           AS description FROM DUAL UNION ALL
  SELECT 'VENDOR' AS name, 'Vendor — list and manage own equipment'  AS description FROM DUAL UNION ALL
  SELECT 'ADMIN'  AS name, 'Administrator — full access'            AS description FROM DUAL
) s
ON (r.name = s.name)
WHEN NOT MATCHED THEN
  INSERT (name, description) VALUES (s.name, s.description);
