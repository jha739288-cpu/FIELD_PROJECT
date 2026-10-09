package com.labmarket.usage;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
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

/** Usage-history API tests: scoping, filters, manual records, immutability, audits (H2, rollback). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UsageLogControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder encoder;
  @Autowired private AuditEventRepository audits;

  private String tokenA;
  private String tokenB;
  private String tokenStaff;
  private long userIdA;
  private long equipmentId;

  @BeforeEach
  void setUp() throws Exception {
    Role student = roles.save(new Role("USER", "Student"));
    Role staff = roles.save(new Role("VENDOR", "Lab staff"));
    roles.save(new Role("ADMIN", "Administrator"));
    userIdA = createUser("stuA", "stuA@example.com", student);
    createUser("stuB", "stuB@example.com", student);
    createUser("stf", "stf@example.com", staff);
    tokenA = login("stuA", "password123");
    tokenB = login("stuB", "password123");
    tokenStaff = login("stf", "password123");
    equipmentId = createEquipment();
  }

  @Test
  void staffManualRecord201WithComputedDuration() throws Exception {
    Instant start = Instant.now().minusSeconds(3700);
    Instant end = Instant.now().minusSeconds(100);

    String body =
        mvc.perform(
                post("/api/v1/usage")
                    .header("Authorization", "Bearer " + tokenStaff)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(manualBody(equipmentId, userIdA, start, end, "walk-in")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.source").value("MANUAL"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.durationSeconds").value(3600))
            .andExpect(jsonPath("$.bookingId").doesNotExist())
            .andExpect(jsonPath("$.username").value("stuA"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = json.readTree(body).get("id").asLong();

    var types =
        audits.findByEntityTypeAndEntityIdOrderById("USAGE", id).stream()
            .map(e -> e.getEventType())
            .toList();
    org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("USAGE_STARTED", "USAGE_ENDED"), types);
  }

  @Test
  void studentManualRecord403() throws Exception {
    Instant start = Instant.now().minusSeconds(3700);
    mvc.perform(
            post("/api/v1/usage")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(manualBody(equipmentId, userIdA, start, Instant.now().minusSeconds(100), null)))
        .andExpect(status().isForbidden());
  }

  @Test
  void manualValidation() throws Exception {
    Instant past = Instant.now().minusSeconds(3600);
    // end before start
    mvc.perform(
            post("/api/v1/usage")
                .header("Authorization", "Bearer " + tokenStaff)
                .contentType(MediaType.APPLICATION_JSON)
                .content(manualBody(equipmentId, userIdA, past, past.minusSeconds(10), null)))
        .andExpect(status().isBadRequest());
    // end in the future
    mvc.perform(
            post("/api/v1/usage")
                .header("Authorization", "Bearer " + tokenStaff)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    manualBody(equipmentId, userIdA, past, Instant.now().plusSeconds(3600), null)))
        .andExpect(status().isBadRequest());
    // unknown equipment
    mvc.perform(
            post("/api/v1/usage")
                .header("Authorization", "Bearer " + tokenStaff)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    manualBody(9999L, userIdA, past, Instant.now().minusSeconds(100), null)))
        .andExpect(status().isNotFound());
    // unknown user
    mvc.perform(
            post("/api/v1/usage")
                .header("Authorization", "Bearer " + tokenStaff)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    manualBody(equipmentId, 9999L, past, Instant.now().minusSeconds(100), null)))
        .andExpect(status().isNotFound());
  }

  @Test
  void qrSessionAppearsInStudentHistory() throws Exception {
    // Full QR flow for stuA: book → confirm → generate → check in.
    long booking = book(tokenA);
    confirm(booking);
    String qr = generateQr(booking, tokenA);
    checkIn(qr, tokenA);

    String body =
        mvc.perform(get("/api/v1/usage/my").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].source").value("QR_CHECKIN"))
            .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.content[0].bookingId").value(booking))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long sessionId = json.readTree(body).get("content").get(0).get("id").asLong();

    // The check-in wrote a USAGE_STARTED audit row scoped to this session.
    // (Scoped by id: audit writes commit independently and outlive test rollback.)
    var types =
        audits.findByEntityTypeAndEntityIdOrderById("USAGE", sessionId).stream()
            .map(e -> e.getEventType())
            .toList();
    org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("USAGE_STARTED"), types);
  }

  @Test
  void scopingAndFilters() throws Exception {
    createManual(tokenStaff, userIdA); // MANUAL for A
    long booking = book(tokenB);
    confirm(booking);
    checkIn(generateQr(booking, tokenB), tokenB); // ACTIVE QR session for B

    // A sees only her own row.
    mvc.perform(get("/api/v1/usage").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    // A asking for B's user id is forbidden.
    mvc.perform(
            get("/api/v1/usage").param("userId", "9999").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isForbidden());
    // Staff sees everything and can filter.
    mvc.perform(get("/api/v1/usage").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(
            get("/api/v1/usage")
                .param("source", "MANUAL")
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            get("/api/v1/usage")
                .param("status", "ACTIVE")
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            get("/api/v1/usage")
                .param("equipmentId", String.valueOf(equipmentId))
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void singleRecordAccessControl() throws Exception {
    long manualId = createManual(tokenStaff, userIdA);
    // Owner reads.
    mvc.perform(get("/api/v1/usage/" + manualId).header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(manualId));
    // Other student forbidden.
    mvc.perform(get("/api/v1/usage/" + manualId).header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isForbidden());
    // Staff reads.
    mvc.perform(get("/api/v1/usage/" + manualId).header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk());
    // Missing is 404.
    mvc.perform(get("/api/v1/usage/9999").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isNotFound());
  }

  @Test
  void historicalRecordsCannotBeModified() throws Exception {
    long manualId = createManual(tokenStaff, userIdA);
    // No update route exists.
    mvc.perform(
            put("/api/v1/usage/" + manualId)
                .header("Authorization", "Bearer " + tokenStaff)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isMethodNotAllowed());
    // No delete route exists.
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/usage/" + manualId)
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isMethodNotAllowed());
  }

  // ---------------------------------------------------------------- helpers

  private long createManual(String staffToken, long forUserId) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/usage")
                    .header("Authorization", "Bearer " + staffToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        manualBody(
                            equipmentId, forUserId,
                            Instant.now().minusSeconds(3700), Instant.now().minusSeconds(100),
                            "walk-in")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private String manualBody(Long equipId, Long forUserId, Instant start, Instant end, String note)
      throws Exception {
    var map = new java.util.HashMap<String, Object>();
    map.put("equipmentId", equipId);
    map.put("userId", forUserId);
    map.put("startTime", start.toString());
    map.put("endTime", end.toString());
    if (note != null) map.put("note", note);
    return json.writeValueAsString(map);
  }

  private long book(String token) throws Exception {
    Instant start = Instant.now().plusSeconds(20 * 60);
    String body =
        mvc.perform(
                post("/api/v1/bookings")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "equipmentId", equipmentId,
                                "startTime", start.toString(),
                                "endTime", start.plusSeconds(3600).toString(),
                                "purpose", "usage lab"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private void confirm(long id) throws Exception {
    mvc.perform(put("/api/v1/bookings/" + id + "/confirm").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk());
  }

  private String generateQr(long bookingId, String token) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/bookings/" + bookingId + "/qr").header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("qrToken").asText();
  }

  private void checkIn(String qr, String token) throws Exception {
    mvc.perform(
            post("/api/v1/bookings/check-in")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qrToken", qr))))
        .andExpect(status().isOk());
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

  private long createUser(String username, String email, Role role) {
    User user = new User();
    user.setUsername(username);
    user.setEmail(email);
    user.setPasswordHash(encoder.encode("password123"));
    user.setEnabled(true);
    user.getRoles().add(role);
    return users.save(user).getId();
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
