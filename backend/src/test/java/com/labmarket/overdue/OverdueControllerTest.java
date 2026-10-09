package com.labmarket.overdue;

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
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.RoleRepository;
import com.labmarket.repository.UserRepository;
import com.labmarket.service.OverdueService;
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

/**
 * Overdue end-to-end: backdated rows stand in for elapsed time (the scheduler
 * itself is disabled in tests), detection runs explicitly, behaviour asserts via HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OverdueControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder encoder;
  @Autowired private BookingRepository bookingRepo;
  @Autowired private AuditEventRepository audits;
  @Autowired private OverdueService overdue;

  private String tokenA;
  private String tokenStaff;
  private long equipmentId;

  @BeforeEach
  void setUp() throws Exception {
    Role student = roles.save(new Role("USER", "Student"));
    Role staff = roles.save(new Role("VENDOR", "Lab staff"));
    roles.save(new Role("ADMIN", "Administrator"));
    createUser("stuA", "stuA@example.com", student);
    createUser("stf", "stf@example.com", staff);
    tokenA = login("stuA", "password123");
    tokenStaff = login("stf", "password123");
    equipmentId = createEquipment();
  }

  @Test
  void detectFlipsBookingAndEquipmentAndRaisesAlert() throws Exception {
    long id = backdateCheckedInBooking();

    org.junit.jupiter.api.Assertions.assertEquals(1, overdue.detectOverdue());

    mvc.perform(get("/api/v1/bookings/" + id).header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OVERDUE"));
    mvc.perform(get("/api/v1/equipment/" + equipmentId).header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentStatus").value("OVERDUE"));
    mvc.perform(get("/api/v1/alerts/overdue").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].bookingId").value(id));

    var detected =
        audits.findByEntityTypeAndEntityIdOrderById("BOOKING", id).stream()
            .map(e -> e.getEventType())
            .toList();
    org.junit.jupiter.api.Assertions.assertTrue(detected.contains("OVERDUE_DETECTED"));
  }

  @Test
  void rerunIsIdempotent() throws Exception {
    long id = backdateCheckedInBooking();

    org.junit.jupiter.api.Assertions.assertEquals(1, overdue.detectOverdue());
    org.junit.jupiter.api.Assertions.assertEquals(0, overdue.detectOverdue());

    mvc.perform(get("/api/v1/alerts/overdue").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    long auditCount =
        audits.findByEntityTypeAndEntityIdOrderById("BOOKING", id).stream()
            .filter(e -> e.getEventType().equals("OVERDUE_DETECTED"))
            .count();
    org.junit.jupiter.api.Assertions.assertEquals(1, auditCount);
  }

  @Test
  void alertsRequireStaff() throws Exception {
    mvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/alerts/overdue").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/alerts").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk());
  }

  @Test
  void resolveWithSessionCompletesAndFrees() throws Exception {
    long id = backdateCheckedInBooking();
    overdue.detectOverdue();
    long alertId = openAlertId();

    mvc.perform(
            put("/api/v1/alerts/" + alertId + "/resolve")
                .header("Authorization", "Bearer " + tokenStaff)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("note", "collected late"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"))
        .andExpect(jsonPath("$.resolutionNote").value("collected late"))
        .andExpect(jsonPath("$.resolvedByUsername").value("stf"));

    mvc.perform(get("/api/v1/bookings/" + id).header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
    mvc.perform(get("/api/v1/equipment/" + equipmentId).header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentStatus").value("AVAILABLE"));

    // Resolving twice is a conflict.
    mvc.perform(
            put("/api/v1/alerts/" + alertId + "/resolve")
                .header("Authorization", "Bearer " + tokenStaff)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict());
  }

  @Test
  void resolveWithoutSessionCancels() throws Exception {
    long id = backdateConfirmedBooking();
    overdue.detectOverdue();
    long alertId = openAlertId();

    mvc.perform(
            put("/api/v1/alerts/" + alertId + "/resolve")
                .header("Authorization", "Bearer " + tokenStaff)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));

    mvc.perform(get("/api/v1/bookings/" + id).header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void resolveMissingIsNotFound() throws Exception {
    mvc.perform(
            put("/api/v1/alerts/9999/resolve")
                .header("Authorization", "Bearer " + tokenStaff)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void alertsListFilters() throws Exception {
    backdateCheckedInBooking();
    overdue.detectOverdue();

    mvc.perform(
            get("/api/v1/alerts").param("status", "OPEN").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            get("/api/v1/alerts").param("type", "OVERDUE").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            get("/api/v1/alerts")
                .param("status", "RESOLVED")
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  // ---------------------------------------------------------------- helpers

  /** Past-end CONFIRMED booking with no session (never picked up). */
  private long backdateConfirmedBooking() throws Exception {
    long id = bookFuture();
    confirm(id);
    backdate(id);
    return id;
  }

  /** Past-end CHECKED_IN booking with an open session (still out). */
  private long backdateCheckedInBooking() throws Exception {
    long id = bookFuture();
    confirm(id);
    String qr =
        mvc.perform(post("/api/v1/bookings/" + id + "/qr").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = json.readTree(qr).get("qrToken").asText();
    // Check in while the window is still valid, then move the window into the past.
    var booking = bookingRepo.findById(id).orElseThrow();
    booking.setStartTime(Instant.now().minusSeconds(300));
    booking.setEndTime(Instant.now().plusSeconds(3600));
    mvc.perform(
            post("/api/v1/bookings/check-in")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qrToken", token))))
        .andExpect(status().isOk());
    backdate(id);
    return id;
  }

  private long bookFuture() throws Exception {
    Instant start = Instant.now().plusSeconds(20 * 60);
    String body =
        mvc.perform(
                post("/api/v1/bookings")
                    .header("Authorization", "Bearer " + tokenA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "equipmentId", equipmentId,
                                "startTime", start.toString(),
                                "endTime", start.plusSeconds(3600).toString(),
                                "purpose", "overdue lab"))))
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

  private void backdate(long id) {
    var booking = bookingRepo.findById(id).orElseThrow();
    booking.setStartTime(Instant.now().minusSeconds(7200));
    booking.setEndTime(Instant.now().minusSeconds(3600));
  }

  private long openAlertId() throws Exception {
    String body =
        mvc.perform(get("/api/v1/alerts/overdue").header("Authorization", "Bearer " + tokenStaff))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("content").get(0).get("id").asLong();
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
