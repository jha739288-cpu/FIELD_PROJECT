package com.labmarket.dashboard;

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

/**
 * Dashboard API tests against real rows. Lab-wide endpoints require
 * LAB_STAFF/ADMIN (students get 403 and use {@code /my-summary}); exact counts,
 * exact utilization math, trend totals and the new analytics endpoints (H2, rollback).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder encoder;

  private String tokenA;
  private String tokenStaff;
  private long userIdA;
  private long equipment1;
  private long equipment2;

  @BeforeEach
  void setUp() throws Exception {
    Role student = roles.save(new Role("STUDENT", "Student"));
    Role staff = roles.save(new Role("LAB_STAFF", "Lab staff"));
    roles.save(new Role("ADMIN", "Administrator"));
    userIdA = createUser("stuA", "stuA@example.com", student);
    createUser("stf", "stf@example.com", staff);
    tokenA = login("stuA", "password123");
    tokenStaff = login("stf", "password123");
    equipment1 = createEquipment("OSC-001");
    equipment2 = createEquipment("CENT-001");
  }

  @Test
  void summaryReflectsRealData() throws Exception {
    seedActivity();

    mvc.perform(get("/api/v1/dashboard/summary").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        // equipment right now: item1 IN_USE (sensor), item2 AVAILABLE
        .andExpect(jsonPath("$.equipment.total").value(2))
        .andExpect(jsonPath("$.equipment.available").value(1))
        .andExpect(jsonPath("$.equipment.inUse").value(1))
        .andExpect(jsonPath("$.equipment.maintenance").value(0))
        // bookings: 1 CONFIRMED + 1 PENDING (the overlap attempt created nothing)
        .andExpect(jsonPath("$.bookings.total").value(2))
        .andExpect(jsonPath("$.bookings.confirmed").value(1))
        .andExpect(jsonPath("$.bookings.pending").value(1))
        // usage: the single MANUAL session, 3600s = 1.0h
        .andExpect(jsonPath("$.usage.sessionsStarted").value(1))
        .andExpect(jsonPath("$.usage.sessionsCompleted").value(1))
        .andExpect(jsonPath("$.usage.totalSeconds").value(3600))
        .andExpect(jsonPath("$.usage.totalHours").value(1.0))
        // sensors: 1 event, 0 stale, 0 faults
        .andExpect(jsonPath("$.sensors.events").value(1))
        .andExpect(jsonPath("$.sensors.staleEvents").value(0))
        .andExpect(jsonPath("$.sensors.faults").value(0))
        // alerts: none yet
        .andExpect(jsonPath("$.alerts.open").value(0))
        .andExpect(jsonPath("$.windowFrom").exists())
        .andExpect(jsonPath("$.computedAt").exists());
  }

  @Test
  void labWideEndpointsForbidStudents() throws Exception {
    String[] paths = {
      "/api/v1/dashboard/summary",
      "/api/v1/dashboard/utilization?from=" + Instant.now().minusSeconds(60) + "&to=" + Instant.now(),
      "/api/v1/dashboard/equipment/" + equipment1,
      "/api/v1/dashboard/usage",
      "/api/v1/dashboard/conflicts",
      "/api/v1/dashboard/bookings",
      "/api/v1/dashboard/sensors"
    };
    for (String path : paths) {
      mvc.perform(get(path).header("Authorization", "Bearer " + tokenA))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void mySummaryIsPersonal() throws Exception {
    seedActivity();

    mvc.perform(get("/api/v1/dashboard/my-summary").header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("stuA"))
        // stuA owns the confirmed booking, the pending booking and the manual session
        .andExpect(jsonPath("$.bookings.total").value(2))
        .andExpect(jsonPath("$.bookings.confirmed").value(1))
        .andExpect(jsonPath("$.usage.totalSeconds").value(3600))
        .andExpect(jsonPath("$.usage.totalHours").value(1.0));

    // Staff have no rows of their own here.
    mvc.perform(get("/api/v1/dashboard/my-summary").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bookings.total").value(0))
        .andExpect(jsonPath("$.usage.totalSeconds").value(0));

    mvc.perform(get("/api/v1/dashboard/my-summary")).andExpect(status().isUnauthorized());
  }

  @Test
  void emptyDatabaseYieldsZeros() throws Exception {
    // No seedActivity: only roles/users/equipment exist, nothing else.
    // (BOOKING_CONFLICT audits commit independently and outlive rollback, so the
    // conflict count is asserted as a delta in usageTrendAndConflicts instead.)
    mvc.perform(get("/api/v1/dashboard/summary").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.equipment.total").value(2))
        .andExpect(jsonPath("$.bookings.total").value(0))
        .andExpect(jsonPath("$.usage.totalSeconds").value(0))
        .andExpect(jsonPath("$.utilizationPercent").value(0.0))
        .andExpect(jsonPath("$.sensors.events").value(0))
        .andExpect(jsonPath("$.sensors.reliability").isEmpty());

    Instant to = Instant.now();
    Instant from = to.minusSeconds(86_400);
    mvc.perform(
            get("/api/v1/dashboard/utilization")
                .param("from", from.toString())
                .param("to", to.toString())
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.equipmentCount").value(2))
        .andExpect(jsonPath("$.totalUsageSeconds").value(0))
        .andExpect(jsonPath("$.utilizationPercent").value(0.0));
  }

  @Test
  void utilizationMathIsExact() throws Exception {
    seedActivity();
    Instant from = Instant.now().minusSeconds(86_400);
    Instant to = Instant.now();

    mvc.perform(
            get("/api/v1/dashboard/utilization")
                .param("from", from.toString())
                .param("to", to.toString())
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        // capacity 2 items × 86400s; usage 3600s → 2.08%
        .andExpect(jsonPath("$.equipmentCount").value(2))
        .andExpect(jsonPath("$.operationalCount").value(2))
        .andExpect(jsonPath("$.totalUsageSeconds").value(3600))
        .andExpect(jsonPath("$.utilizationPercent").value(2.08))
        .andExpect(jsonPath("$.items[0].equipmentCode").value("OSC-001"))
        .andExpect(jsonPath("$.items[0].usageSeconds").value(3600))
        .andExpect(jsonPath("$.items[0].utilizationPercent").value(4.17));

    // Filtered to the idle item: zero usage.
    mvc.perform(
            get("/api/v1/dashboard/utilization")
                .param("from", from.toString())
                .param("to", to.toString())
                .param("equipmentId", String.valueOf(equipment2))
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.equipmentCount").value(1))
        .andExpect(jsonPath("$.totalUsageSeconds").value(0))
        .andExpect(jsonPath("$.utilizationPercent").value(0.0));
  }

  @Test
  void maintenanceItemsLeaveCapacity() throws Exception {
    seedActivity();
    setMaintenance(equipment2, "OUT_OF_SERVICE");
    Instant from = Instant.now().minusSeconds(86_400);

    mvc.perform(
            get("/api/v1/dashboard/utilization")
                .param("from", from.toString())
                .param("to", Instant.now().toString())
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.equipmentCount").value(2))
        .andExpect(jsonPath("$.operationalCount").value(1))
        // capacity now 1 × 86400s → 4.17%
        .andExpect(jsonPath("$.utilizationPercent").value(4.17));
  }

  @Test
  void equipmentDashboardShowsLiveAndWindowed() throws Exception {
    seedActivity();

    mvc.perform(
            get("/api/v1/dashboard/equipment/" + equipment1)
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.equipmentCode").value("OSC-001"))
        .andExpect(jsonPath("$.currentStatus").value("IN_USE"))
        .andExpect(jsonPath("$.bookingsByStatus.CONFIRMED").value(1))
        .andExpect(jsonPath("$.sessions").value(1))
        .andExpect(jsonPath("$.usageSeconds").value(3600))
        .andExpect(jsonPath("$.recentBookings").isArray());

    mvc.perform(
            get("/api/v1/dashboard/equipment/9999").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isNotFound());
  }

  @Test
  void bookingAnalyticsGroupsRows() throws Exception {
    seedActivity();
    Instant from = Instant.now().minusSeconds(2 * 86_400);

    mvc.perform(
            get("/api/v1/dashboard/bookings")
                .param("from", from.toString())
                .param("to", Instant.now().toString())
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.byStatus.CONFIRMED").value(1))
        .andExpect(jsonPath("$.byStatus.PENDING").value(1))
        .andExpect(jsonPath("$.cancelled").value(0))
        .andExpect(jsonPath("$.overdue").value(0))
        .andExpect(jsonPath("$.byEquipment[0].equipmentCode").value("OSC-001"));

    // Window excluding the seed: zeros, not errors.
    mvc.perform(
            get("/api/v1/dashboard/bookings")
                .param("from", Instant.now().minusSeconds(10 * 86_400).toString())
                .param("to", Instant.now().minusSeconds(9 * 86_400).toString())
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void sensorAnalyticsReportsMix() throws Exception {
    seedActivity();

    mvc.perform(
            get("/api/v1/dashboard/sensors").header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.byStatus.IN_USE").value(1))
        .andExpect(jsonPath("$.faults").value(0))
        .andExpect(jsonPath("$.offline").value(0))
        .andExpect(jsonPath("$.reliability").value(1.0))
        .andExpect(jsonPath("$.byEquipment[0].equipmentCode").value("OSC-001"));
  }

  @Test
  void usageTrendAndConflicts() throws Exception {
    // Audit rows commit independently and outlive test rollback: assert the delta.
    long conflictsBefore = conflictCount();
    seedActivity();
    Instant from = Instant.now().minusSeconds(2 * 86_400);

    mvc.perform(
            get("/api/v1/dashboard/usage")
                .param("from", from.toString())
                .param("to", Instant.now().toString())
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.granularity").value("DAY"));

    // Trend totals across points equal the single 3600s session + 2 created bookings.
    String trend =
        mvc.perform(
                get("/api/v1/dashboard/usage").header("Authorization", "Bearer " + tokenStaff))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long usageTotal = 0;
    long createdTotal = 0;
    var points = json.readTree(trend).get("points");
    for (var p : points) {
      usageTotal += p.get("usageSeconds").asLong();
      createdTotal += p.get("bookingsCreated").asLong();
    }
    org.junit.jupiter.api.Assertions.assertEquals(3600L, usageTotal);
    org.junit.jupiter.api.Assertions.assertEquals(2L, createdTotal);

    // The rejected overlap attempt was audited exactly once. (Audit rows commit
    // independently and outlive test rollback, so assert the delta, not the total.)
    org.junit.jupiter.api.Assertions.assertEquals(conflictsBefore + 1, conflictCount());
  }

  private long conflictCount() throws Exception {
    String body =
        mvc.perform(get("/api/v1/dashboard/conflicts").header("Authorization", "Bearer " + tokenStaff))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("conflictAttempts").asLong();
  }

  @Test
  void dashboardRequiresAuthentication() throws Exception {
    mvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/dashboard/usage")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/dashboard/my-summary")).andExpect(status().isUnauthorized());
  }

  @Test
  void invalidWindowIsBadRequest() throws Exception {
    Instant now = Instant.now();
    mvc.perform(
            get("/api/v1/dashboard/utilization")
                .param("from", now.toString())
                .param("to", now.minusSeconds(10).toString())
                .header("Authorization", "Bearer " + tokenStaff))
        .andExpect(status().isBadRequest());
  }

  /** One manual session (3600s) + 1 confirmed + 1 pending booking + 1 overlap 409 + 1 sensor event. */
  private void seedActivity() throws Exception {
    Instant start = Instant.now().minusSeconds(3700);
    mvc.perform(
            post("/api/v1/usage")
                .header("Authorization", "Bearer " + tokenStaff)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "equipmentId", equipment1,
                            "userId", userIdA,
                            "startTime", start.toString(),
                            "endTime", start.plusSeconds(3600).toString(),
                            "note", "seed"))))
        .andExpect(status().isCreated());

    long b1 = book(tokenA, equipment1, 2, 4);
    confirm(b1);
    // Overlapping attempt → 409 + BOOKING_CONFLICT audit.
    Instant s = Instant.now().plusSeconds(3 * 3600);
    mvc.perform(
            post("/api/v1/bookings")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "equipmentId", equipment1,
                            "startTime", s.toString(),
                            "endTime", s.plusSeconds(2 * 3600).toString(),
                            "purpose", "clash"))))
        .andExpect(status().isConflict());
    book(tokenA, equipment2, 6, 8); // pending

    String key = provisionKey(equipment1);
    mvc.perform(
            post("/api/v1/sensors/events")
                .header("X-Sensor-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "equipmentCode", "OSC-001",
                            "status", "IN_USE",
                            "currentValue", 1.8,
                            "timestamp", Instant.now().toString()))))
        .andExpect(status().isCreated());
  }

  private void setMaintenance(long equipId, String maintenance) throws Exception {
    mvc.perform(
            put("/api/v1/equipment/" + equipId)
                .header("Authorization", "Bearer " + tokenStaff)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "name", "Centrifuge",
                            "category", "Electronics",
                            "condition", "GOOD",
                            "currentStatus", "MAINTENANCE",
                            "maintenanceStatus", maintenance,
                            "laboratory", "Lab A"))))
        .andExpect(status().isOk());
  }

  private long book(String token, long equipId, long fromH, long toH) throws Exception {
    Instant start = Instant.now().plusSeconds(fromH * 3600);
    String body =
        mvc.perform(
                post("/api/v1/bookings")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "equipmentId", equipId,
                                "startTime", start.toString(),
                                "endTime", start.plusSeconds((toH - fromH) * 3600).toString(),
                                "purpose", "dash lab"))))
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

  private String provisionKey(long equipId) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/equipment/" + equipId + "/sensor-key")
                    .header("Authorization", "Bearer " + tokenStaff))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("sensorKey").asText();
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
                                "name", code.startsWith("CENT") ? "Centrifuge" : "Oscilloscope",
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
