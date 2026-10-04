package com.labmarket.repository;

import com.labmarket.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Role}. Rows are seeded — this repo exists for lookups only. */
public interface RoleRepository extends JpaRepository<Role, Long> {

  Optional<Role> findByName(String name);
}
