package com.labmarket.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.labmarket.dto.BookingCreateRequest;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.Role;
import com.labmarket.entity.User;
import com.labmarket.exception.ConflictException;
import com.labmarket.repository.AlertRepository;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.QrTokenRepository;
import com.labmarket.repository.RoleRepository;
import com.labmarket.repository.SensorEventRepository;
import com.labmarket.repository.UsageSessionRepository;
import com.labmarket.repository.UserRepository;
import com.labmarket.service.BookingService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Concurrency proof for the overlap guarantee: two overlapping PENDING bookings
 * confirmed at the same instant must never both succeed. The equipment row lock
 * serializes the check-then-act sequence.
 *
 * <p>Deliberately NOT {@code @Transactional}: each thread needs its own committed
 * transaction (like two real HTTP requests). State is cleaned manually so no
 * rows leak into other test classes sharing the context.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencyTest {

  @Autowired private BookingService bookings;
  @Autowired private BookingRepository bookingRepo;
  @Autowired private EquipmentRepository equipmentRepo;
  @Autowired private UserRepository userRepo;
  @Autowired private RoleRepository roleRepo;
  @Autowired private AlertRepository alertRepo;
  @Autowired private UsageSessionRepository sessionRepo;
  @Autowired private QrTokenRepository qrRepo;
  @Autowired private SensorEventRepository sensorRepo;
  @Autowired private PasswordEncoder encoder;
  @Autowired private JdbcTemplate jdbc;

  private long bookingA;
  private long bookingB;

  @BeforeEach
  void setUp() {
    clean();
    Role student = roleRepo.save(new Role("STUDENT", "Student"));
    Role staff = roleRepo.save(new Role("LAB_STAFF", "Lab staff"));
    user("raceStuA", student);
    user("raceStuB", student);
    user("raceStf", staff);
    Equipment item = new Equipment();
    item.setEquipmentCode("RACE-001");
    item.setName("Race scope");
    item.setCategory("Electronics");
    item.setCurrentStatus(EquipmentStatus.AVAILABLE);
    item.setMaintenanceStatus(MaintenanceStatus.OPERATIONAL);
    equipmentRepo.save(item);
    Instant base = Instant.now().plusSeconds(3600);
    // Overlapping PENDING bookings: A=[base, base+2h], B=[base+1h, base+3h].
    bookingA =
        bookings
            .create(
                "raceStuA",
                new BookingCreateRequest(
                    item.getId(), base, base.plusSeconds(7200), "race A"))
            .id();
    bookingB =
        bookings
            .create(
                "raceStuB",
                new BookingCreateRequest(
                    item.getId(), base.plusSeconds(3600), base.plusSeconds(10800), "race B"))
            .id();
  }

  @AfterEach
  void tearDown() {
    clean();
  }

  @Test
  void concurrentConfirmsYieldExactlyOneConfirmation() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Callable<String> confirmA =
          () -> {
            start.await(10, TimeUnit.SECONDS);
            bookings.confirm("raceStf", bookingA);
            return "A";
          };
      Callable<String> confirmB =
          () -> {
            start.await(10, TimeUnit.SECONDS);
            bookings.confirm("raceStf", bookingB);
            return "B";
          };
      Future<String> f1 = pool.submit(confirmA);
      Future<String> f2 = pool.submit(confirmB);
      start.countDown(); // release both threads at once

      int confirmed = 0;
      int conflicts = 0;
      for (Future<String> f : List.of(f1, f2)) {
        try {
          f.get(30, TimeUnit.SECONDS);
          confirmed++;
        } catch (java.util.concurrent.ExecutionException e) {
          if (e.getCause() instanceof ConflictException) {
            conflicts++;
          } else {
            throw e;
          }
        }
      }
      assertEquals(1, confirmed, "exactly one confirm must succeed");
      assertEquals(1, conflicts, "exactly one confirm must conflict");

      Map<Long, BookingStatus> states =
          bookingRepo.findAllById(List.of(bookingA, bookingB)).stream()
              .collect(Collectors.toMap(b -> b.getId(), b -> b.getStatus()));
      assertTrue(states.containsValue(BookingStatus.CONFIRMED));
      assertTrue(states.containsValue(BookingStatus.PENDING));
    } finally {
      pool.shutdownNow();
    }
  }

  private void user(String username, Role role) {
    User u = new User();
    u.setUsername(username);
    u.setEmail(username + "@example.com");
    u.setPasswordHash(encoder.encode("password123"));
    u.setEnabled(true);
    u.getRoles().add(role);
    userRepo.save(u);
  }

  private void clean() {
    alertRepo.deleteAll();
    sessionRepo.deleteAll();
    qrRepo.deleteAll();
    sensorRepo.deleteAll();
    bookingRepo.deleteAll();
    equipmentRepo.deleteAll();
    jdbc.update("DELETE FROM user_roles");
    jdbc.update("DELETE FROM users");
    jdbc.update("DELETE FROM roles");
  }
}
