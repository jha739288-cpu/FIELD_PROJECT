package com.labmarket.roles;

import static org.hamcrest.Matchers.hasSize;
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

/**
 * End-to-end role flows: USER lifecycle, VENDOR lifecycle + ownership walls,
 * ADMIN management, cross-role 403s, overlap 409, ADMIN self-registration block.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleSystemTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private RoleRepository roles;
  @Autowired private PasswordEncoder encoder;

  private String adminToken;

  @BeforeEach
  void setUp() throws Exception {
    roles.save(new Role("ADMIN", "Administrator"));
    roles.save(new Role("USER", "User"));
    roles.save(new Role("VENDOR", "Vendor"));
    createUser("adm", "adm@example.com", "ADMIN");
    adminToken = login("adm", "password123");
  }

  @Test
  void test1_userLifecycle() throws Exception {
    // Register as USER.
    String reg =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody("ann", "ann@example.com", "USER")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.roles[0]").value("USER"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = json.readTree(reg).get("token").asText();

    // Dashboard (personal), browse, book, my bookings, cancel.
    mvc.perform(get("/api/v1/dashboard/my-summary").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/equipment").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    long equipmentId = createEquipment(adminToken, "U-001");
    Instant start = Instant.now().plusSeconds(7200);
    long bookingId = book(token, equipmentId, start, start.plusSeconds(3600));
    mvc.perform(get("/api/v1/bookings/my").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(put("/api/v1/bookings/" + bookingId + "/cancel").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void test2_vendorLifecycleAndOwnershipWalls() throws Exception {
    String vendorToken = registerAndLogin("venA", "vena@example.com", "VENDOR");
    long ownId = createEquipmentAs(vendorToken, "V-A-001");

    // Mine shows it; marketplace shows it.
    mvc.perform(
            get("/api/v1/equipment").param("mine", "true").header("Authorization", "Bearer " + vendorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(get("/api/v1/equipment").header("Authorization", "Bearer " + vendorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    // Edit own equipment.
    mvc.perform(
            put("/api/v1/equipment/" + ownId)
                .header("Authorization", "Bearer " + vendorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Renamed")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed"));

    // Second vendor cannot touch it.
    String vendorB = registerAndLogin("venB", "venb@example.com", "VENDOR");
    mvc.perform(
            put("/api/v1/equipment/" + ownId)
                .header("Authorization", "Bearer " + vendorB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody("Hijacked")))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/equipment/" + ownId).header("Authorization", "Bearer " + vendorB))
        .andExpect(status().isForbidden());
    // Still intact.
    mvc.perform(get("/api/v1/equipment/" + ownId).header("Authorization", "Bearer " + vendorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed"));
  }

  @Test
  void test3_adminManagesPlatform() throws Exception {
    registerAndLogin("ann", "ann@example.com", "USER");
    String vendorToken = registerAndLogin("ven", "ven@example.com", "VENDOR");
    createEquipmentAs(vendorToken, "V-001");

    // Users list + search + role filter.
    mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3));
    mvc.perform(
            get("/api/v1/users").param("role", "VENDOR").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    // Vendors view with counts.
    mvc.perform(get("/api/v1/users/vendors").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].equipmentCount").value(1));

    // Disable then re-enable + re-role the user; never self.
    long annId = userId("ann");
    mvc.perform(
            put("/api/v1/users/" + annId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
    mvc.perform(
            put("/api/v1/users/" + annId + "/roles")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"VENDOR\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("VENDOR"));
    long admId = userId("adm");
    mvc.perform(
            put("/api/v1/users/" + admId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isForbidden());

    // Overview has real counts.
    mvc.perform(get("/api/v1/dashboard/admin/overview").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalUsers").value(3))
        .andExpect(jsonPath("$.usersByRole.VENDOR").value(2));

    // Vendor dashboard for the vendor.
    mvc.perform(get("/api/v1/dashboard/vendor").header("Authorization", "Bearer " + vendorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalEquipment").value(1));
  }

  @Test
  void test4_userCannotReachAdmin() throws Exception {
    String token = registerAndLogin("ann", "ann@example.com", "USER");
    mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/dashboard/admin/overview").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void test5_userCannotUseVendorApi() throws Exception {
    String token = registerAndLogin("ann", "ann@example.com", "USER");
    mvc.perform(
            post("/api/v1/equipment")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("equipmentCode", "X-1", "name", "X", "category", "Y"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void test7_overlapStillRejected() throws Exception {
    String token = registerAndLogin("ann", "ann@example.com", "USER");
    long equipmentId = createEquipment(adminToken, "U-777");
    Instant s1 = Instant.now().plusSeconds(7200);
    long b1 = book(token, equipmentId, s1, s1.plusSeconds(3600));
    confirm(b1);
    Instant s2 = s1.plusSeconds(1800);
    mvc.perform(
            post("/api/v1/bookings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingBody(equipmentId, s2, s2.plusSeconds(3600))))
        .andExpect(status().isConflict());
  }

  @Test
  void adminCannotSelfRegister() throws Exception {
    var map = new HashMap<String, Object>();
    map.put("username", "evil");
    map.put("email", "evil@example.com");
    map.put("password", "password123");
    map.put("role", "ADMIN");
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(map)))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------- helpers

  private String registerBody(String username, String email, String role) throws Exception {
    var map = new HashMap<String, Object>();
    map.put("username", username);
    map.put("email", email);
    map.put("password", "password123");
    if (role != null) map.put("role", role);
    return json.writeValueAsString(map);
  }

  private String registerAndLogin(String username, String email, String role) throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(username, email, role)))
        .andExpect(status().isCreated());
    return login(username, "password123");
  }

  private long createEquipment(String token, String code) throws Exception {
    return createEquipmentAs(token, code);
  }

  private long createEquipmentAs(String token, String code) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/equipment")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "equipmentCode", code,
                                "name", "Item " + code,
                                "category", "Electronics",
                                "laboratory", "Lab A"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private String updateBody(String name) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "name", name,
            "category", "Electronics",
            "condition", "GOOD",
            "currentStatus", "AVAILABLE",
            "maintenanceStatus", "OPERATIONAL",
            "laboratory", "Lab A"));
  }

  private long book(String token, long equipmentId, Instant start, Instant end) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/bookings")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bookingBody(equipmentId, start, end)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private String bookingBody(long equipmentId, Instant start, Instant end) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "equipmentId", equipmentId,
            "startTime", start.toString(),
            "endTime", end.toString(),
            "purpose", "role test"));
  }

  private void confirm(long id) throws Exception {
    mvc.perform(put("/api/v1/bookings/" + id + "/confirm").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());
  }

  private long userId(String username) {
    return users.findByUsername(username).orElseThrow().getId();
  }

  private void createUser(String username, String email, String roleName) {
    Role role = roles.findByName(roleName).orElseThrow();
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
