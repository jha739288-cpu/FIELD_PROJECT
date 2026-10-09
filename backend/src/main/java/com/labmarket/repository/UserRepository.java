package com.labmarket.repository;

import com.labmarket.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link User}. Queries return entities; services map to DTOs. */
public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByUsername(String username);

  Optional<User> findByEmail(String email);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  List<User> findTop5ByOrderByCreatedAtDesc();

  @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :role")
  long countByRoleName(@Param("role") String role);

  /** Admin user search: text over username/email, optional role membership and status. */
  @Query(
      """
      SELECT u FROM User u
      WHERE (:pattern IS NULL
             OR LOWER(u.username) LIKE :pattern ESCAPE '|'
             OR LOWER(u.email) LIKE :pattern ESCAPE '|')
        AND (:role IS NULL OR EXISTS (SELECT r FROM u.roles r WHERE r.name = :role))
        AND (:enabled IS NULL OR u.enabled = :enabled)
      """)
  Page<User> searchAdmin(
      @Param("pattern") String pattern,
      @Param("role") String role,
      @Param("enabled") Boolean enabled,
      Pageable pageable);
}
