package com.labmarket.booking;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.labmarket.repository.QrTokenRepository;
import com.labmarket.repository.RoleRepository;
import com.labmarket.repository.UserRepository;
import java.time.Instant;
import java.util.List;
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
 * QR check-in/out workflow tests: generation rules, every check-in rejection,
 * the happy path (IN_USE → AVAILABLE with measured duration) and the audit trail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QrCheckInControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder encoder;
  @Autowired private BookingRepository bookingRepo;
  @Autowired private QrTokenRepository qrTokens;
  @Autowired private AuditEventRepository audits;

  private String tokenA;
  private String tokenB;
  private String tokenStaff;
  private long equipmentId;

  @BeforeEach
  void setUp() throws Exception {
    Role student = roles.save(new Role("STUDENT", "Student"));
    Role staff = roles.save(new Role("LAB_STAFF", "Lab staff"));
    roles.save(new Role("ADMIN", "Administrator"));
    createUser("stuA", "stuA@example.com", student);
    createUser("stuB", "stuB@example.com", student);
    createUser("stf", "stf@example.com", staff);
    tokenA = login("stuA", "password123");
    tokenB = login("stuB", "password123");
    tokenStaff = login("stf", "password123");
    equipmentId = createEquipment();
  }

  @Test
  void generateQrOwner201AndStoresOnlyHash() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id);

    String body =
        mvc.perform(post("/api/v1/bookings/" + id + "/qr").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.qrToken", notNullValue()))
            .andExpect(jsonPath("$.bookingId").value(id))
            .andExpect(jsonPath("$.expiresAt", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String raw = json.readTree(body).get("qrToken").asText();

    var stored = qrTokens.findByBookingIdAndUsedFalse(id);
    assertEquals(1, stored.size());
    // The raw token must NOT be persisted — only its 64-hex SHA-256 hash.
    assertEquals(64, stored.get(0).getTokenHash().length());
    org.junit.jupiter.api.Assertions.assertNotEquals(raw, stored.get(0).getTokenHash());

    assertEquals(
        List.of("QR_GENERATED"), auditTypes(id));
  }

  @Test
  void generateQrOtherStudent403() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id);
    mvc.perform(post("/api/v1/bookings/" + id + "/qr").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isForbidden());
  }

  @Test
  void generateQrPendingBooking409() throws Exception {
    long id = bookSoon(tokenA); // still PENDING
    mvc.perform(post("/api/v1/bookings/" + id + "/qr").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isConflict());
  }

  @Test
  void checkInSuccessFlipsStatusAndOpensSession() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id);
    String qr = generateQr(id, tokenA);

    mvc.perform(
            post("/api/v1/bookings/check-in")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qrToken", qr))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.booking.status").value("CHECKED_IN"))
        .andExpect(jsonPath("$.session.startedAt", notNullValue()))
        .andExpect(jsonPath("$.session.endedAt").doesNotExist())
        .andExpect(jsonPath("$.session.durationSeconds").doesNotExist());

    // Equipment is now IN_USE.
    mvc.perform(get("/api/v1/equipment/" + equipmentId).header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentStatus").value("IN_USE"));

    assertEquals(List.of("QR_GENERATED", "CHECKED_IN"), auditTypes(id));
  }

  @Test
  void checkInInvalidToken404AndAudited() throws Exception {
    mvc.perform(
            post("/api/v1/bookings/check-in")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qrToken", "not-a-real-token"))))
        .andExpect(status().isNotFound());
    assertEquals(1, countEvents("QR_VALIDATION_FAILED"));
  }

  @Test
  void checkInExpiredToken410() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id);
    String qr = generateQr(id, tokenA);
    qrTokens.findByBookingIdAndUsedFalse(id).forEach(t -> t.setExpiresAt(Instant.now().minusSeconds(60)));

    mvc.perform(
            post("/api/v1/bookings/check-in")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qrToken", qr))))
        .andExpect(status().isGone());
    assertEquals(List.of("QR_GENERATED", "QR_VALIDATION_FAILED"), auditTypes(id));
  }

  @Test
  void checkInUsedToken409() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id);
    String qr = generateQr(id, tokenA);
    checkIn(qr, tokenA); // consumes it

    mvc.perform(
            post("/api/v1/bookings/check-in")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qrToken", qr))))
        .andExpect(status().isConflict());
  }

  @Test
  void checkInWrongUser403() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id);
    String qr = generateQr(id, tokenA);

    mvc.perform(
            post("/api/v1/bookings/check-in")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qrToken", qr))))
        .andExpect(status().isForbidden());
    assertEquals(List.of("QR_GENERATED", "QR_VALIDATION_FAILED"), auditTypes(id));
  }

  @Test
  void checkInTooEarly409() throws Exception {
    // Starts in 5h — well before the 30-minute check-in window.
    long id = bookHours(tokenA, 5, 7);
    confirm(id);
    String qr = generateQr(id, tokenA);

    mvc.perform(
            post("/api/v1/bookings/check-in")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qrToken", qr))))
        .andExpect(status().isConflict());
  }

  @Test
  void checkInAfterEnd409() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id);
    String qr = generateQr(id, tokenA);
    // Move the booking window into the past but keep the token itself valid.
    var booking = bookingRepo.findById(id).orElseThrow();
    booking.setStartTime(Instant.now().minusSeconds(7200));
    booking.setEndTime(Instant.now().minusSeconds(3600));
    qrTokens.findByBookingIdAndUsedFalse(id).forEach(t -> t.setExpiresAt(Instant.now().plusSeconds(3600)));

    mvc.perform(
            post("/api/v1/bookings/check-in")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qrToken", qr))))
        .andExpect(status().isConflict());
  }

  @Test
  void checkInCancelledBooking409() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id);
    String qr = generateQr(id, tokenA);
    mvc.perform(put("/api/v1/bookings/" + id + "/cancel").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/v1/bookings/check-in")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qrToken", qr))))
        .andExpect(status().isConflict());
  }

  @Test
  void checkInMaintenanceEquipment409() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id);
    String qr = generateQr(id, tokenA);
    setMaintenanceStatus("OUT_OF_SERVICE");

    mvc.perform(
            post("/api/v1/bookings/check-in")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("qrToken", qr))))
        .andExpect(status().isConflict());
  }

  @Test
  void checkOutSuccessCompletesWithDuration() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id);
    checkIn(generateQr(id, tokenA), tokenA);

    mvc.perform(put("/api/v1/bookings/" + id + "/checkout").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.booking.status").value("COMPLETED"))
        .andExpect(jsonPath("$.session.endedAt", notNullValue()))
        .andExpect(jsonPath("$.session.durationSeconds", notNullValue()));

    // Equipment is back to AVAILABLE.
    mvc.perform(get("/api/v1/equipment/" + equipmentId).header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentStatus").value("AVAILABLE"));

    assertEquals(List.of("QR_GENERATED", "CHECKED_IN", "CHECKED_OUT"), auditTypes(id));
  }

  @Test
  void checkOutTwice409() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id);
    checkIn(generateQr(id, tokenA), tokenA);
    mvc.perform(put("/api/v1/bookings/" + id + "/checkout").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk());
    mvc.perform(put("/api/v1/bookings/" + id + "/checkout").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isConflict());
  }

  @Test
  void checkOutByOtherStudent403() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id);
    checkIn(generateQr(id, tokenA), tokenA);
    mvc.perform(put("/api/v1/bookings/" + id + "/checkout").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isForbidden());
  }

  @Test
  void checkOutWithoutCheckIn409() throws Exception {
    long id = bookSoon(tokenA);
    confirm(id); // CONFIRMED but never checked in
    mvc.perform(put("/api/v1/bookings/" + id + "/checkout").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isConflict());
  }

  // ---------------------------------------------------------------- helpers

  /** Booking starting in ~20 min (inside the 30-min check-in window), lasting 1h. */
  private long bookSoon(String token) throws Exception {
    Instant start = Instant.now().plusSeconds(20 * 60);
    return bookAt(token, start, start.plusSeconds(3600));
  }

  private long bookHours(String token, long fromH, long toH) throws Exception {
    return bookAt(
        token, Instant.now().plusSeconds(fromH * 3600), Instant.now().plusSeconds(toH * 3600));
  }

  private long bookAt(String token, Instant start, Instant end) throws Exception {
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
                                "endTime", end.toString(),
                                "purpose", "qr lab"))))
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

  private List<String> auditTypes(long bookingId) {
    return audits.findByEntityTypeAndEntityIdOrderById("BOOKING", bookingId).stream()
        .map(e -> e.getEventType())
        .toList();
  }

  private long countEvents(String type) {
    // NOTE: audit writes commit independently (REQUIRES_NEW) and therefore survive the
    // per-test rollback — scope to rows with no booking so earlier tests cannot pollute this.
    return audits.findAll().stream()
        .filter(e -> e.getEventType().equals(type) && e.getEntityId() == null)
        .count();
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

  private void setMaintenanceStatus(String maintenance) throws Exception {
    mvc.perform(
            put("/api/v1/equipment/" + equipmentId)
                .header("Authorization", "Bearer " + tokenStaff)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "name", "Oscilloscope",
                            "category", "Electronics",
                            "condition", "GOOD",
                            "currentStatus", "MAINTENANCE",
                            "maintenanceStatus", maintenance,
                            "laboratory", "Lab A"))))
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
