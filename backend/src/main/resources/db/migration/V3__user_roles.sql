-- ============================================================================
-- Flyway V3 — executable copy of database/schema/02_user_roles.sql.
-- Do not edit after it has been applied anywhere; add a new V4__... instead.
-- ============================================================================

CREATE TABLE user_roles (
  user_id NUMBER NOT NULL,
  role_id NUMBER NOT NULL,
  CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

COMMENT ON TABLE user_roles IS 'Join table: which roles each user holds. Managed only by auth/user-management code.';
