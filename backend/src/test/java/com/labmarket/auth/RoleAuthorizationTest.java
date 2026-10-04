package com.labmarket.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmarket.entity.Role;
import com.labmarket.entity.User;
import com.labmarket.repository.RoleRepository;
import com.labmarket.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves role-based authorization end to end: STUDENT vs LAB_STAFF vs ADMIN
 * against {@code @PreAuthorize} rules, plus the 401/403 split.
 *
 * <p>The probe controller stands in for the equipment/booking controllers that
 * will carry these same annotations in later modules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(RoleAuthorizationTest.ProbeConfig.class)
class RoleAuthorizationTest {

  @TestConfiguration
  static class ProbeConfig {
    @Bean
    ProbeController probeController() {
      return new ProbeController();
    }
  }

  @RestController
  @RequestMapping("/api/probe")
  static class ProbeController {
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> admin() {
      return Map.of("area", "admin");
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('LAB_STAFF', 'ADMIN')")
    public Map<String, String> staff() {
      return Map.of("area", "staff");
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder encoder;

  private String studentToken;
  private String staffToken;
  private String adminToken;

  @BeforeEach
  void setUp() throws Exception {
    Role student = roles.save(new Role("STUDENT", "Student"));
    Role staff = roles.save(new Role("LAB_STAFF", "Lab staff"));
    Role admin = roles.save(new Role("ADMIN", "Administrator"));

    createUser("stu", "stu@example.com", "password123", student);
    createUser("stf", "stf@example.com", "password123", staff);
    createUser("adm", "adm@example.com", "password123", admin);

    studentToken = login("stu", "password123");
    staffToken = login("stf", "password123");
    adminToken = login("adm", "password123");
  }

  @Test
  void anonymousProbeReturns401() throws Exception {
    mvc.perform(get("/api/probe/admin")).andExpect(status().isUnauthorized());
  }

  @Test
  void studentForbiddenOnAdminProbe() throws Exception {
    mvc.perform(get("/api/probe/admin").header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void staffForbiddenOnAdminProbe() throws Exception {
    mvc.perform(get("/api/probe/admin").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminAllowedOnAdminProbe() throws Exception {
    mvc.perform(get("/api/probe/admin").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.area").value("admin"));
  }

  @Test
  void studentForbiddenOnStaffProbe() throws Exception {
    mvc.perform(get("/api/probe/staff").header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void staffAllowedOnStaffProbe() throws Exception {
    mvc.perform(get("/api/probe/staff").header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.area").value("staff"));
  }

  @Test
  void adminAllowedOnStaffProbe() throws Exception {
    mvc.perform(get("/api/probe/staff").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  private void createUser(String username, String email, String password, Role role) {
    User user = new User();
    user.setUsername(username);
    user.setEmail(email);
    user.setPasswordHash(encoder.encode(password));
    user.setEnabled(true);
    user.getRoles().add(role);
    users.save(user);
  }

  private String login(String username, String password) throws Exception {
    String response =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("username", username, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).get("token").asText();
  }
}
