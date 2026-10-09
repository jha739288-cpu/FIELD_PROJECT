package com.labmarket.auth;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmarket.entity.Role;
import com.labmarket.repository.RoleRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end auth flows against the REAL security chain (H2, rolled back per test).
 * Roles are seeded here because the test profile disables Flyway.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private RoleRepository roles;

  @BeforeEach
  void seedRoles() {
    if (roles.findByName("USER").isEmpty()) {
      roles.save(new Role("USER", "Student"));
      roles.save(new Role("VENDOR", "Lab staff"));
      roles.save(new Role("ADMIN", "Administrator"));
    }
  }

  @Test
  void registerStudentReturns201WithTokenAndNeverAHash() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("sara", "sara@example.com", "password123", "Sara")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token", notNullValue()))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.username").value("sara"))
        .andExpect(jsonPath("$.roles", contains("USER")))
        .andExpect(content().string(not(containsString("password"))))
        .andExpect(content().string(not(containsString("$2a$"))));
  }

  @Test
  void registerDuplicateUsernameReturns409() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("sara", "sara@example.com", "password123", "Sara")))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("sara", "other@example.com", "password123", "Sara")))
        .andExpect(status().isConflict());
  }

  @Test
  void registerDuplicateEmailReturns409() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("sara", "sara@example.com", "password123", "Sara")))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("sara2", "sara@example.com", "password123", "Sara")))
        .andExpect(status().isConflict());
  }

  @Test
  void registerInvalidInputReturns400() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ab", "not-an-email", "short", null)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void loginSucceedsAfterRegister() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("sara", "sara@example.com", "password123", "Sara")))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "sara", "password", "password123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token", notNullValue()))
        .andExpect(jsonPath("$.roles", contains("USER")));
  }

  @Test
  void loginWrongPasswordReturns401() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("sara", "sara@example.com", "password123", "Sara")))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "sara", "password", "wrong-pass-1"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginUnknownUserReturns401() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "ghost", "password", "whatever123"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void meWithoutTokenReturns401() throws Exception {
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void meWithTokenReturnsProfileWithoutHash() throws Exception {
    String registerResponse =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("sara", "sara@example.com", "password123", "Sara")))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = json.readTree(registerResponse).get("token").asText();

    mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("sara"))
        .andExpect(jsonPath("$.email").value("sara@example.com"))
        .andExpect(jsonPath("$.roles", contains("USER")))
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(content().string(not(containsString("$2a$"))));
  }

  @Test
  void healthStaysPublicUnderRealChain() throws Exception {
    mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
  }

  private String body(String username, String email, String password, String fullName)
      throws Exception {
    return json.writeValueAsString(
        Map.of(
            "username", username,
            "email", email,
            "password", password,
            "fullName", fullName == null ? "" : fullName));
  }
}
