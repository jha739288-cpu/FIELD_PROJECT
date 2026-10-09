package com.labmarket.seed;

import com.labmarket.entity.Role;
import com.labmarket.entity.User;
import com.labmarket.repository.RoleRepository;
import com.labmarket.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Development/admin bootstrap: creates the first ADMIN account from environment
 * variables. Never hard-codes credentials — without APP_ADMIN_USERNAME and
 * APP_ADMIN_PASSWORD it logs and does nothing. Idempotent: existing usernames
 * are left untouched.
 */
@Component
@Order(10)
public class AdminSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

  private final UserRepository users;
  private final RoleRepository roles;
  private final PasswordEncoder encoder;
  private final String adminUsername;
  private final String adminEmail;
  private final String adminPassword;
  private final String adminFullName;

  public AdminSeedRunner(
      UserRepository users,
      RoleRepository roles,
      PasswordEncoder encoder,
      @Value("${app.seed.admin-username:}") String adminUsername,
      @Value("${app.seed.admin-email:}") String adminEmail,
      @Value("${app.seed.admin-password:}") String adminPassword,
      @Value("${app.seed.admin-full-name:Administrator}") String adminFullName) {
    this.users = users;
    this.roles = roles;
    this.encoder = encoder;
    this.adminUsername = adminUsername;
    this.adminEmail = adminEmail;
    this.adminPassword = adminPassword;
    this.adminFullName = adminFullName;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (adminUsername.isBlank() || adminPassword.isBlank()) {
      log.info("Admin seed skipped (set APP_ADMIN_USERNAME/APP_ADMIN_PASSWORD to enable)");
      return;
    }
    if (users.existsByUsername(adminUsername)) {
      log.info("Admin '{}' already exists, skipping seed", adminUsername);
      return;
    }
    Role admin =
        roles
            .findByName("ADMIN")
            .orElseThrow(() -> new IllegalStateException("ADMIN role is not seeded"));
    User user = new User();
    user.setUsername(adminUsername);
    user.setEmail(adminEmail.isBlank() ? adminUsername + "@localhost" : adminEmail);
    user.setPasswordHash(encoder.encode(adminPassword));
    user.setFullName(adminFullName);
    user.setEnabled(true);
    user.getRoles().add(admin);
    users.save(user);
    log.info("Seeded ADMIN account '{}'", adminUsername);
  }
}
