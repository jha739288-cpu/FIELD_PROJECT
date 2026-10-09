package com.labmarket.booking;

import static org.hamcrest.Matchers.hasSize;
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

/** Calendar API tests: schedule, availability (booked/free/maintenance) and lanes (H2, rollback). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookingCalendarControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder encoder;

  private String tokenA;
  private String tokenB;
  private String tokenStaff;
  private long equipmentId;

  @BeforeEach
  void setUp() throws Exception {
    Role student = roles.save(new Role("USER", "Student"));
    Role staff = roles.save(new Role("VENDOR", "Lab staff"));
    roles.save(new Role("ADMIN", "Administrator"));
    createUser("stuA", "stuA@example.com", student);
    createUser("stuB", "stuB@example.com", student);
    createUser("stf", "stf@example.com", staff);
    tokenA = login("stuA", "password123");
    tokenB = login("stuB", "password123");
    tokenStaff = login("stf", "password123");
    equipmentId = createEquipment("OSC-001", "Oscilloscope");
  }

  @Test
  void scheduleShowsSlotsWithRedaction() throws Exception {
    // A: [2h,4h] confirmed. B: [5h,7h] pending (no overlap with A).
    confirm(book(tokenA, 2, 4));
    book(tokenB, 5, 7);

    // Owner A sees her own purpose, but B's purpose/username are redacted.
    mvc.perform(
            get("/api/v1/bookings/equipment/" + equipmentId)
                .param("from", hours(0))
                .param("to", hours(8))
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.equipmentCode").value("OSC-001"))
        .andExpect(jsonPath("$.currentStatus").value("AVAILABLE"))
        .andExpect(jsonPath("$.slots", hasSize(2)))
        .andExpect(jsonPath("$.slots[0].status").value("CONFIRMED"))
        .andExpect(jsonPath("$.slots[0].purpose").value("lab work A"))
        .andExpect(jsonPath("$.slots[0].username").value("stuA"))
        .andExpect(jsonPath("$.slots[1].status").value("PENDING"))
        .andExpect(jsonPath("$.slots[1].purpose").doesNotExist())
        .andExpect(jsonPath("$.slots[1].username").doesNotExist());

    // Staff sees every purpose and username.
    mvc.perform(
            get("/api/v1/bookings/equipment/" + equipmentId)
                .param("from", hours(0))
                .param("to", hours(8))
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slots[1].purpose").value("lab work B"))
        .andExpect(jsonPath("$.slots[1].username").value("stuB"));
  }

  @Test
  void scheduleMissingWindowParamIsBadRequest() throws Exception {
    mvc.perform(
            get("/api/v1/bookings/equipment/" + equipmentId)
                .param("to", hours(8))
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isBadRequest());
  }

  @Test
  void scheduleWindowValidationAndMissingEquipment() throws Exception {
    // from >= to
    mvc.perform(
            get("/api/v1/bookings/equipment/" + equipmentId)
                .param("from", hours(8))
                .param("to", hours(0))
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isBadRequest());
    // unknown equipment
    mvc.perform(
            get("/api/v1/bookings/equipment/9999")
                .param("from", hours(0))
                .param("to", hours(8))
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());
    // anonymous
    mvc.perform(
            get("/api/v1/bookings/equipment/" + equipmentId).param("from", hours(0)).param("to", hours(8)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void availabilityReportsBookedPendingAndFree() throws Exception {
    confirm(book(tokenA, 2, 4)); // blocking [2h,4h]
    book(tokenB, 5, 6); // pending [5h,6h]

    mvc.perform(
            get("/api/v1/bookings/equipment/" + equipmentId + "/availability")
                .param("from", hours(0))
                .param("to", hours(8))
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookable").value(true))
        .andExpect(jsonPath("$.maintenance").value(false))
        .andExpect(jsonPath("$.currentStatus").value("AVAILABLE"))
        .andExpect(jsonPath("$.bookedPeriods", hasSize(1)))
        .andExpect(jsonPath("$.bookedPeriods[0].status").value("CONFIRMED"))
        .andExpect(jsonPath("$.pendingPeriods", hasSize(1)))
        // free gaps: [0h,2h] and [4h,8h] (pending does not consume availability)
        .andExpect(jsonPath("$.freePeriods", hasSize(2)));
  }

  @Test
  void availabilityClipsBookingsToWindow() throws Exception {
    // Booking [2h,10h] confirmed; window [4h,6h] clips both ends.
    confirm(book(tokenA, 2, 10));
    mvc.perform(
            get("/api/v1/bookings/equipment/" + equipmentId + "/availability")
                .param("from", hours(4))
                .param("to", hours(6))
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookedPeriods", hasSize(1)))
        .andExpect(jsonPath("$.freePeriods", hasSize(0)));
  }

  @Test
  void availabilityFlagsMaintenance() throws Exception {
    setMaintenanceStatus("OUT_OF_SERVICE");
    mvc.perform(
            get("/api/v1/bookings/equipment/" + equipmentId + "/availability")
                .param("from", hours(0))
                .param("to", hours(8))
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookable").value(false))
        .andExpect(jsonPath("$.maintenance").value(true))
        .andExpect(jsonPath("$.freePeriods", hasSize(0)))
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void adjacentConfirmedBookingsMergeIntoOneBlock() throws Exception {
    // Shared boundary instant: B starts EXACTLY when A ends.
    Instant start = Instant.now().plusSeconds(2 * 3600);
    Instant boundary = Instant.now().plusSeconds(4 * 3600);
    Instant end = Instant.now().plusSeconds(6 * 3600);
    confirm(bookAt(tokenA, start, boundary));
    confirm(bookAt(tokenB, boundary, end));
    mvc.perform(
            get("/api/v1/bookings/equipment/" + equipmentId + "/availability")
                .param("from", hours(0))
                .param("to", hours(8))
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookedPeriods", hasSize(2)))
        // free gaps: [0h,start] and [end,8h] — nothing between the adjacent bookings
        .andExpect(jsonPath("$.freePeriods", hasSize(2)));
  }

  @Test
  void calendarReturnsLanesForAllEquipment() throws Exception {
    long second = createEquipment("CENT-001", "Centrifuge");
    confirm(book(tokenA, 2, 4)); // on OSC-001

    mvc.perform(
            get("/api/v1/bookings/calendar")
                .param("from", hours(0))
                .param("to", hours(8))
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedules", hasSize(2)));

    // Filtered to one item.
    mvc.perform(
            get("/api/v1/bookings/calendar")
                .param("from", hours(0))
                .param("to", hours(8))
                .param("equipmentId", String.valueOf(second))
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedules", hasSize(1)))
        .andExpect(jsonPath("$.schedules[0].equipmentCode").value("CENT-001"))
        .andExpect(jsonPath("$.schedules[0].slots", hasSize(0)));
  }

  private static String hours(long h) {
    return Instant.now().plusSeconds(h * 3600).toString();
  }

  private long book(String token, long fromH, long toH) throws Exception {
    return bookAt(token, Instant.now().plusSeconds(fromH * 3600), Instant.now().plusSeconds(toH * 3600));
  }

  private long bookAt(String token, Instant start, Instant end) throws Exception {
    String who = token.equals(tokenA) ? "A" : "B";
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
                                "purpose", "lab work " + who))))
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

  private long createEquipment(String code, String name) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/equipment")
                    .header("Authorization", "Bearer " + tokenStaff)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "equipmentCode", code,
                                "name", name,
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
