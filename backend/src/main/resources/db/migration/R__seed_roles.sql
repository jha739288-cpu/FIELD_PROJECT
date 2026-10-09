-- ============================================================================
-- Flyway repeatable migration — executable copy of database/data/01_roles_seed.sql.
-- Re-runs when its checksum changes; the MERGE is idempotent so this is safe.
-- ============================================================================

MERGE INTO roles r
USING (
  SELECT 'USER'   AS name, 'User — book and use equipment'            AS description FROM DUAL UNION ALL
  SELECT 'VENDOR' AS name, 'Vendor — list and manage own equipment'  AS description FROM DUAL UNION ALL
  SELECT 'ADMIN'  AS name, 'Administrator — full access'             AS description FROM DUAL
) s
ON (r.name = s.name)
WHEN NOT MATCHED THEN
  INSERT (name, description) VALUES (s.name, s.description);
