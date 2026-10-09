package com.labmarket.prediction;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Role;
import com.labmarket.entity.User;
import com.labmarket.repository.BookingRepository;
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

/**
 * Prediction API tests on real rows: deterministic override, cold start,
 * method switch, validation, and backtesting with backdated history (H2, rollback).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PredictionControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder encoder;
  @Autowired private BookingRepository bookingRepo;

  private String tokenA;
  private String tokenStaff;
  private long equipmentId;
  private long emptyEquipmentId;

  @BeforeEach
  void setUp() throws Exception {
    Role student = roles.save(new Role("USER", "Student"));
    Role staff = roles.save(new Role("VENDOR", "Lab staff"));
    roles.save(new Role("ADMIN", "Administrator"));
    createUser("stuA", "stuA@example.com", student);
    createUser("stf", "stf@example.com", staff);
    tokenA = login("stuA", "password123");
    tokenStaff = login("stf", "password123");
    equipmentId = createEquipment("OSC-001");
    emptyEquipmentId = createEquipment("OSC-002");
  }

  @Test
  void futureBookingForcesUnavailable() throws Exception {
    // Confirmed [base+2h, base+4h]: every slot inside is UNAVAILABLE with p=0.
    // Single base instant: two independent now() calls could straddle a clock
    // tick and append a sliver slot (flaky hasSize).
    Instant base = Instant.now();
    long id = bookAt(tokenA, base.plusSeconds(2 * 3600), base.plusSeconds(4 * 3600));
    confirm(id);

    mvc.perform(
            get("/api/v1/predictions/equipment/" + equipmentId)
                .param("from", base.plusSeconds(2 * 3600).toString())
                .param("to", base.plusSeconds(4 * 3600).toString())
                .param("slotMinutes", "60")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.method").value("empirical"))
        .andExpect(jsonPath("$.disclaimer").exists())
        .andExpect(jsonPath("$.slots", hasSize(2)))
        .andExpect(jsonPath("$.slots[0].predictedStatus").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.slots[0].probabilityAvailable").value(0.0))
        .andExpect(jsonPath("$.slots[0].confidence").value(1.0));
  }

  @Test
  void coldStartIsNeutral() throws Exception {
    mvc.perform(
            get("/api/v1/predictions/equipment/" + emptyEquipmentId)
                .param("from", hours(48))
                .param("to", hours(50))
                .param("slotMinutes", "60")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots[0].predictedStatus").value("LIMITED"))
        .andExpect(jsonPath("$.slots[0].probabilityAvailable").value(0.5));
  }

  @Test
  void naiveMethodAvailable() throws Exception {
    mvc.perform(
            get("/api/v1/predictions/equipment/" + emptyEquipmentId)
                .param("from", hours(48))
                .param("to", hours(49))
                .param("method", "naive")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.method").value("naive"))
        .andExpect(jsonPath("$.slots[0].predictedStatus").value("AVAILABLE"));
  }

  @Test
  void validation() throws Exception {
    // unknown method
    mvc.perform(
            get("/api/v1/predictions/equipment/" + equipmentId)
                .param("from", hours(48))
                .param("to", hours(49))
                .param("method", "oracle-magic")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isBadRequest());
    // missing equipment
    mvc.perform(
            get("/api/v1/predictions/equipment/9999")
                .param("from", hours(48))
                .param("to", hours(49))
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());
    // anonymous
    mvc.perform(
            get("/api/v1/predictions/equipment/" + equipmentId)
                .param("from", hours(48))
                .param("to", hours(49)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void evaluationNeedsHistory() throws Exception {
    // Empty item: refused, not invented.
    mvc.perform(
            get("/api/v1/predictions/equipment/" + emptyEquipmentId + "/evaluation")
                .param("from", daysAgo(7))
                .param("to", daysAgo(6))
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isBadRequest());
    // Students cannot evaluate.
    mvc.perform(
            get("/api/v1/predictions/equipment/" + emptyEquipmentId + "/evaluation")
                .param("from", daysAgo(7))
                .param("to", daysAgo(6))
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isForbidden());
  }

  @Test
  void evaluationComparesAgainstActuals() throws Exception {
    // A COMPLETED booking 3 days ago, 10:00–12:00 UTC: backtest that morning.
    Instant base = Instant.now().minusSeconds(3 * 86400);
    Instant dayStart =
        base.atZone(java.time.ZoneOffset.UTC).toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant();
    long id = book(tokenA, 50, 52); // future booking, then moved into the past
    confirm(id);
    var booking = bookingRepo.findById(id).orElseThrow();
    booking.setStatus(BookingStatus.COMPLETED);
    booking.setStartTime(dayStart.plusSeconds(10 * 3600));
    booking.setEndTime(dayStart.plusSeconds(12 * 3600));

    mvc.perform(
            get("/api/v1/predictions/equipment/" + equipmentId + "/evaluation")
                .param("from", dayStart.toString())
                .param("to", dayStart.plusSeconds(12 * 3600).toString())
                .param("slotMinutes", "120")
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slotsEvaluated").value(6))
        .andExpect(jsonPath("$.accuracy").exists())
        .andExpect(jsonPath("$.brierScore").exists())
        .andExpect(jsonPath("$.note").exists());
  }

  private static String hours(long h) {
    return Instant.now().plusSeconds(h * 3600).toString();
  }

  private static String daysAgo(long d) {
    return Instant.now().minusSeconds(d * 86400).toString();
  }

  private long book(String token, long fromH, long toH) throws Exception {
    Instant start = Instant.now().plusSeconds(fromH * 3600);
    return bookAt(token, start, start.plusSeconds((toH - fromH) * 3600));
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
                                "purpose", "pred lab"))))
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

  private long createEquipment(String code) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/equipment")
                    .header("Authorization", "Bearer " + tokenStaff)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "equipmentCode", code,
                                "name", "Scope " + code,
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
