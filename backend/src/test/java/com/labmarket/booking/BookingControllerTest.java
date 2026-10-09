package com.labmarket.booking;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

/**
 * Full-stack booking tests: the spec example (10:00–12:00 vs 11:00–13:00),
 * edge cases, access control and the pending/confirm lifecycle (H2, rollback).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookingControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder encoder;

  private String tokenA; // student A
  private String tokenB; // student B
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
    equipmentId = createEquipment();
  }

  @Test
  void studentBooksPending201() throws Exception {
    Instant start = hoursFromNow(2);
    mvc.perform(
            post("/api/v1/bookings")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(equipmentId, start, start.plusSeconds(7200), "oscilloscope lab")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.username").value("stuA"))
        .andExpect(jsonPath("$.equipmentCode").value("OSC-001"))
        .andExpect(content().string(not(containsString("password"))));
  }

  @Test
  void overlappingWithConfirmedBookingIsRejected() throws Exception {
    // Booking A: [now+2h, now+4h], confirmed by staff.
    long bookingA = book(tokenA, hoursFromNow(2), hoursFromNow(4));
    confirm(bookingA);
    // Booking B: [now+3h, now+5h] — overlaps A, must be rejected with a clear message.
    mvc.perform(
            post("/api/v1/bookings")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(equipmentId, hoursFromNow(3), hoursFromNow(5), "needs the scope")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("overlapping")));
  }

  @Test
  void backToBackBookingIsAllowed() throws Exception {
    long bookingA = book(tokenA, hoursFromNow(2), hoursFromNow(4));
    confirm(bookingA);
    // Starts exactly when A ends — half-open ranges do not overlap.
    mvc.perform(
            post("/api/v1/bookings")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(equipmentId, hoursFromNow(4), hoursFromNow(6), "next slot")))
        .andExpect(status().isCreated());
  }

  @Test
  void pendingDoesNotBlockCreateButBlocksConfirm() throws Exception {
    long pendingA = book(tokenA, hoursFromNow(2), hoursFromNow(4));
    // Same range as a pending request is still creatable...
    long pendingB = book(tokenB, hoursFromNow(2), hoursFromNow(4));
    confirm(pendingA);
    // ...but only one of them can ever be confirmed.
    mvc.perform(
            put("/api/v1/bookings/" + pendingB + "/confirm")
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isConflict());
  }

  @Test
  void endBeforeStartReturns400() throws Exception {
    mvc.perform(
            post("/api/v1/bookings")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(equipmentId, hoursFromNow(4), hoursFromNow(2), "bad range")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void zeroDurationReturns400() throws Exception {
    Instant point = hoursFromNow(2);
    mvc.perform(
            post("/api/v1/bookings")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(equipmentId, point, point, "zero")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void bookingInPastReturns400() throws Exception {
    mvc.perform(
            post("/api/v1/bookings")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    bookBody(
                        equipmentId,
                        Instant.now().minusSeconds(7200),
                        Instant.now().minusSeconds(3600),
                        "past")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void bookingMissingEquipmentReturns404() throws Exception {
    Instant start = hoursFromNow(2);
    mvc.perform(
            post("/api/v1/bookings")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(9999L, start, start.plusSeconds(3600), "ghost item")))
        .andExpect(status().isNotFound());
  }

  @Test
  void bookingEquipmentUnderMaintenanceReturns409() throws Exception {
    setMaintenance("OUT_OF_SERVICE");
    Instant start = hoursFromNow(2);
    mvc.perform(
            post("/api/v1/bookings")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookBody(equipmentId, start, start.plusSeconds(3600), "broken item")))
        .andExpect(status().isConflict());
  }

  @Test
  void ownerCanCancelButNotTwice() throws Exception {
    long id = book(tokenA, hoursFromNow(2), hoursFromNow(4));
    mvc.perform(put("/api/v1/bookings/" + id + "/cancel").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mvc.perform(put("/api/v1/bookings/" + id + "/cancel").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isConflict());
  }

  @Test
  void otherStudentCannotCancelOrView() throws Exception {
    long id = book(tokenA, hoursFromNow(2), hoursFromNow(4));
    mvc.perform(put("/api/v1/bookings/" + id + "/cancel").header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/bookings/" + id).header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isForbidden());
    // Staff can view anyone's booking.
    mvc.perform(get("/api/v1/bookings/" + id).header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk());
  }

  @Test
  void myListsOnlyOwnBookings() throws Exception {
    book(tokenA, hoursFromNow(2), hoursFromNow(4));
    book(tokenB, hoursFromNow(6), hoursFromNow(8));
    mvc.perform(get("/api/v1/bookings/my").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].username").value("stuA"));
  }

  @Test
  void listStudentSeesOwnStaffSeesAll() throws Exception {
    book(tokenA, hoursFromNow(2), hoursFromNow(4));
    book(tokenB, hoursFromNow(6), hoursFromNow(8));
    mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void staffRejectFlow() throws Exception {
    long id = book(tokenA, hoursFromNow(2), hoursFromNow(4));
    mvc.perform(
            put("/api/v1/bookings/" + id + "/reject").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"));
    // A rejected booking can no longer be confirmed.
    mvc.perform(
            put("/api/v1/bookings/" + id + "/confirm").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isConflict());
  }

  @Test
  void studentCannotConfirm() throws Exception {
    long id = book(tokenA, hoursFromNow(2), hoursFromNow(4));
    mvc.perform(put("/api/v1/bookings/" + id + "/confirm").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isForbidden());
  }

  @Test
  void getMissingBookingReturns404() throws Exception {
    mvc.perform(get("/api/v1/bookings/9999").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isNotFound());
  }

  private static Instant hoursFromNow(long hours) {
    return Instant.now().plusSeconds(hours * 3600);
  }

  private long book(String token, Instant start, Instant end) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/bookings")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bookBody(equipmentId, start, end, "lab work")))
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

  private String bookBody(Long equipId, Instant start, Instant end, String purpose) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "equipmentId", equipId,
            "startTime", start.toString(),
            "endTime", end.toString(),
            "purpose", purpose));
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

  private void setMaintenance(String maintenanceStatus) throws Exception {
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
                            "maintenanceStatus", maintenanceStatus,
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
