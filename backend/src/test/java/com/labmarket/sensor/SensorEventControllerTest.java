package com.labmarket.sensor;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmarket.entity.Role;
import com.labmarket.entity.User;
import com.labmarket.repository.AuditEventRepository;
import com.labmarket.repository.RoleRepository;
import com.labmarket.repository.UserRepository;
import java.time.Instant;
import java.util.HashMap;
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

/** Sensor API tests: device auth, validation, staleness, mapping and history auth (H2, rollback). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SensorEventControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder encoder;
  @Autowired private AuditEventRepository audits;

  private String tokenStaff;
  private String tokenStudent;
  private long equipmentId;
  private String deviceKey;

  @BeforeEach
  void setUp() throws Exception {
    Role student = roles.save(new Role("USER", "Student"));
    Role staff = roles.save(new Role("VENDOR", "Lab staff"));
    roles.save(new Role("ADMIN", "Administrator"));
    createUser("stu", "stu@example.com", student);
    createUser("stf", "stf@example.com", staff);
    tokenStudent = login("stu", "password123");
    tokenStaff = login("stf", "password123");
    equipmentId = createEquipment();
    deviceKey = provisionKey();
  }

  @Test
  void validInUseEventStoredMappedAndAudited() throws Exception {
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "IN_USE", 1.8, Instant.now())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.stale").value(false))
        .andExpect(jsonPath("$.appliedEquipmentStatus").value("IN_USE"));

    mvc.perform(get("/api/v1/equipment/" + equipmentId).header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentStatus").value("IN_USE"));

    // Scoped to this equipment: audit writes commit independently (REQUIRES_NEW)
    // and outlive the per-test rollback, so earlier tests' rows are still present.
    var sensorAudits =
        audits.findAll().stream()
            .filter(
                e ->
                    e.getEventType().equals("SENSOR_EVENT")
                        && Long.valueOf(equipmentId).equals(e.getEntityId()))
            .toList();
    org.junit.jupiter.api.Assertions.assertEquals(1, sensorAudits.size());
  }

  @Test
  void missingOrWrongKeyIsUnauthorized() throws Exception {
    mvc.perform(
            post("/api/v1/sensors/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "IDLE", 0.0, Instant.now())))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "IDLE", 0.0, Instant.now())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownEquipmentCodeIsUnauthorized() throws Exception {
    // Security: unauthenticated callers must not distinguish unknown codes from bad keys.
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("NOPE-999", "IN_USE", 1.0, Instant.now())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void invalidStatusEnumIsBadRequest() throws Exception {
    var map = new HashMap<String, Object>();
    map.put("equipmentCode", "OSC-001");
    map.put("status", "MELTDOWN");
    map.put("currentValue", 1.0);
    map.put("timestamp", Instant.now().toString());
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidValuesAreBadRequest() throws Exception {
    // negative current
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "IN_USE", -3.0, Instant.now())))
        .andExpect(status().isBadRequest());
    // future timestamp
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "IDLE", 0.0, Instant.now().plusSeconds(3600))))
        .andExpect(status().isBadRequest());
    // ancient timestamp
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "IDLE", 0.0, Instant.now().minusSeconds(100_000))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void delayedEventIsAcceptedButFlaggedStale() throws Exception {
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "IN_USE", 1.5, Instant.now().minusSeconds(600))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.stale").value(true));
  }

  @Test
  void offlineAndFaultMapping() throws Exception {
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "OFFLINE", 0.0, Instant.now())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.appliedEquipmentStatus").value("SENSOR_OFFLINE"));

    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "FAULT", 9.9, Instant.now())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.appliedEquipmentStatus").value("MAINTENANCE"));

    // MAINTENANCE is staff-owned: later IDLE reports must not clear it.
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "IDLE", 0.0, Instant.now())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.appliedEquipmentStatus").value("MAINTENANCE"));
  }

  @Test
  void keyRotationInvalidatesOldKey() throws Exception {
    String rotated = provisionKey();
    // Old key rejected...
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "IDLE", 0.0, Instant.now())))
        .andExpect(status().isUnauthorized());
    // ...new key works.
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", rotated)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "IDLE", 0.0, Instant.now())))
        .andExpect(status().isCreated());
  }

  @Test
  void duplicateEventIsStoredTwice() throws Exception {
    // Events are observations, not commands: retries/dupes are stored, never merged.
    String body = eventBody("OSC-001", "IDLE", 0.0, Instant.now());
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              post("/api/v1/sensors/events")
                  .header("X-Sensor-Key", deviceKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isCreated());
    }
  }

  @Test
  void historyRequiresStaff() throws Exception {
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", deviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody("OSC-001", "IDLE", 0.0, Instant.now())))
        .andExpect(status().isCreated());

    mvc.perform(
            get("/api/v1/sensors/events")
                .param("equipmentCode", "OSC-001")
                .header("Authorization", "Bearer " + tokenStudent))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/sensors/events")
                .param("equipmentCode", "OSC-001")
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)));
  }

  @Test
  void provisionKeyRequiresStaff() throws Exception {
    mvc.perform(
            post("/api/v1/equipment/" + equipmentId + "/sensor-key")
                .header("Authorization", "Bearer " + tokenStudent))
        .andExpect(status().isForbidden());
  }

  // ---------------------------------------------------------------- helpers

  private String eventBody(String code, String status, Double amps, Instant at) throws Exception {
    var map = new HashMap<String, Object>();
    map.put("equipmentCode", code);
    map.put("status", status);
    if (amps != null) map.put("currentValue", amps);
    map.put("timestamp", at.toString());
    return json.writeValueAsString(map);
  }

  private String provisionKey() throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/equipment/" + equipmentId + "/sensor-key")
                    .header("Authorization", "Bearer " + tokenStaff))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sensorKey").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("sensorKey").asText();
  }

  private long createEquipment() throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/equipment")
                    .header("Authorization", "Bearer " + tokenStaff)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "equipmentCode", "OSC-001",
                                "name", "Oscilloscope",
                                "category", "Electronics",
                                "laboratory", "Lab A"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
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
