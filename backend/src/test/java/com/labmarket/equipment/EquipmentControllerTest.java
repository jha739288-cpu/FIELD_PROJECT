package com.labmarket.equipment;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmarket.entity.Role;
import com.labmarket.entity.User;
import com.labmarket.repository.RoleRepository;
import com.labmarket.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Full-stack equipment tests: auth, roles, pagination, search, filters, validation, rules. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EquipmentControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder encoder;

  private String studentToken;
  private String staffToken;

  @BeforeEach
  void setUp() throws Exception {
    Role student = roles.save(new Role("USER", "Student"));
    Role staff = roles.save(new Role("VENDOR", "Lab staff"));
    roles.save(new Role("ADMIN", "Administrator"));
    createUser("stu", "stu@example.com", student);
    createUser("stf", "stf@example.com", staff);
    studentToken = login("stu", "password123");
    staffToken = login("stf", "password123");
  }

  @Test
  void deleteEquipmentWithBookingsIsConflictNotServerError() throws Exception {
    long id = create("OSC-001", staffToken);
    // A booking references the item: the FK must surface as 409, never 500.
    Instant start = Instant.now().plusSeconds(3600);
    mvc.perform(
            post("/api/v1/bookings")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "equipmentId", id,
                            "startTime", start.toString(),
                            "endTime", start.plusSeconds(3600).toString(),
                            "purpose", "blocks delete"))))
        .andExpect(status().isCreated());
    mvc.perform(delete("/api/v1/equipment/" + id).header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isConflict());
  }

  @Test
  void unknownSortPropertyIsBadRequest() throws Exception {
    mvc.perform(
            get("/api/v1/equipment")
                .param("sort", "noSuchField,asc")
                .header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anonymousCreateReturns401() throws Exception {
    mvc.perform(
            post("/api/v1/equipment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("OSC-001")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void studentCreateReturns403() throws Exception {
    mvc.perform(
            post("/api/v1/equipment")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("OSC-001")))
        .andExpect(status().isForbidden());
  }

  @Test
  void staffCreateReturns201WithDefaults() throws Exception {
    mvc.perform(
            post("/api/v1/equipment")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("OSC-001")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id", notNullValue()))
        .andExpect(jsonPath("$.equipmentCode").value("OSC-001"))
        .andExpect(jsonPath("$.condition").value("GOOD"))
        .andExpect(jsonPath("$.currentStatus").value("AVAILABLE"))
        .andExpect(jsonPath("$.maintenanceStatus").value("OPERATIONAL"))
        .andExpect(jsonPath("$.createdByUsername").value("stf"));
  }

  @Test
  void duplicateCodeReturns409() throws Exception {
    create("OSC-001", staffToken);
    mvc.perform(
            post("/api/v1/equipment")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("OSC-001")))
        .andExpect(status().isConflict());
  }

  @Test
  void invalidBodyReturns400() throws Exception {
    mvc.perform(
            post("/api/v1/equipment")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void inconsistentStatusReturns409() throws Exception {
    mvc.perform(
            post("/api/v1/equipment")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "equipmentCode", "OSC-009",
                            "name", "Broken scope",
                            "category", "Electronics",
                            "currentStatus", "AVAILABLE",
                            "maintenanceStatus", "OUT_OF_SERVICE"))))
        .andExpect(status().isConflict());
  }

  @Test
  void studentCanListAndView() throws Exception {
    long id = create("OSC-001", staffToken);
    mvc.perform(get("/api/v1/equipment").header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/v1/equipment/" + id).header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.equipmentCode").value("OSC-001"));
  }

  @Test
  void getMissingReturns404() throws Exception {
    mvc.perform(get("/api/v1/equipment/9999").header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void listSupportsPaginationSearchAndFilters() throws Exception {
    create("OSC-001", staffToken);
    create("OSC-002", staffToken);
    create("CENT-001", staffToken);

    // pagination envelope
    mvc.perform(
            get("/api/v1/equipment").param("size", "2").header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.page").value(0));

    // free-text search (case-insensitive, matches name/code)
    mvc.perform(
            get("/api/v1/equipment")
                .param("q", "oscilloscope")
                .header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));

    // category filter
    mvc.perform(
            get("/api/v1/equipment")
                .param("category", "Centrifuges")
                .header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    // laboratory filter
    mvc.perform(
            get("/api/v1/equipment")
                .param("laboratory", "Lab A")
                .header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(
            get("/api/v1/equipment")
                .param("laboratory", "Nowhere Lab")
                .header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));

    // invalid enum value
    mvc.perform(
            get("/api/v1/equipment")
                .param("status", "BROKEN")
                .header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void statusFilterWorks() throws Exception {
    create("OSC-001", staffToken);
    long id = create("OSC-002", staffToken);
    updateStatus(id, "MAINTENANCE", "IN_MAINTENANCE", staffToken);

    mvc.perform(
            get("/api/v1/equipment")
                .param("status", "MAINTENANCE")
                .header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].equipmentCode").value("OSC-002"));
  }

  @Test
  void staffUpdateSucceedsStudentForbidden() throws Exception {
    long id = create("OSC-001", staffToken);

    mvc.perform(
            put("/api/v1/equipment/" + id)
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Hacked", "MAINTENANCE", "IN_MAINTENANCE")))
        .andExpect(status().isForbidden());

    mvc.perform(
            put("/api/v1/equipment/" + id)
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Calibrated scope", "MAINTENANCE", "IN_MAINTENANCE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Calibrated scope"))
        .andExpect(jsonPath("$.currentStatus").value("MAINTENANCE"))
        .andExpect(jsonPath("$.equipmentCode").value("OSC-001"));
  }

  @Test
  void staffDeleteSucceedsThenNotFound() throws Exception {
    long id = create("OSC-001", staffToken);

    mvc.perform(delete("/api/v1/equipment/" + id).header("Authorization", "Bearer " + studentToken))
        .andExpect(status().isForbidden());

    mvc.perform(delete("/api/v1/equipment/" + id).header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/equipment/" + id).header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteInUseReturns409() throws Exception {
    long id = create("OSC-001", staffToken);
    updateStatus(id, "IN_USE", "OPERATIONAL", staffToken);

    mvc.perform(delete("/api/v1/equipment/" + id).header("Authorization", "Bearer " + staffToken))
        .andExpect(status().isConflict());
  }

  private long create(String code, String token) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/equipment")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody(code)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private String createBody(String code) throws Exception {
    boolean centrifuge = code.startsWith("CENT");
    return json.writeValueAsString(
        Map.of(
            "equipmentCode", code,
            "name", centrifuge ? "Centrifuge" : "Oscilloscope",
            "category", centrifuge ? "Centrifuges" : "Electronics",
            "manufacturer", "Acme",
            "model", "X-100",
            "laboratory", "Lab A"));
  }

  private String updateBody(String name, String status, String maintenance) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "name", name,
            "category", "Electronics",
            "condition", "GOOD",
            "currentStatus", status,
            "maintenanceStatus", maintenance,
            "laboratory", "Lab A"));
  }

  private void updateStatus(long id, String status, String maintenance, String token)
      throws Exception {
    mvc.perform(
            put("/api/v1/equipment/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Oscilloscope", status, maintenance)))
        .andExpect(status().isOk());
  }

  private void createUser(String username, String email, Role role) {
    User user = new User();
    user.setUsername(username);
    user.setEmail(email);
    user.setPasswordHash(encoder.encode("password123"));
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
