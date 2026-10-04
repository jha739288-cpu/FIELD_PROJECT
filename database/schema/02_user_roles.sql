-- ============================================================================
-- 02_user_roles.sql — USER_ROLES join table (Module 1: authentication).
-- Authored copy; the executable copy is backend/.../db/migration/V3__user_roles.sql.
-- Many-to-many: one user holds one or more of STUDENT / LAB_STAFF / ADMIN.
-- ============================================================================

CREATE TABLE user_roles (
  user_id NUMBER NOT NULL,
  role_id NUMBER NOT NULL,
  CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

COMMENT ON TABLE user_roles IS 'Join table: which roles each user holds. Managed only by auth/user-management code.';
