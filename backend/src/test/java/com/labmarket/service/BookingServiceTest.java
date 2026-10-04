package com.labmarket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labmarket.dto.BookingCreateRequest;
import com.labmarket.entity.Booking;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.Role;
import com.labmarket.entity.User;
import com.labmarket.exception.ConflictException;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.mapper.BookingMapper;
import com.labmarket.repository.AuditEventRepository;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.QrTokenRepository;
import com.labmarket.repository.UsageSessionRepository;
import com.labmarket.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/** Pure unit tests for booking rules — especially conflict detection (no Spring context). */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

  @Mock private BookingRepository bookings;
  @Mock private EquipmentRepository equipment;
  @Mock private UserRepository users;
  @Mock private QrTokenRepository qrTokens;
  @Mock private UsageSessionRepository sessions;
  @Mock private AuditEventRepository auditEvents;

  private BookingService service;
  private SimpleMeterRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    // Real mapper and audit service (both stateless apart from persistence).
    service =
        new BookingService(
            bookings, equipment, users, qrTokens, sessions, new AuditService(auditEvents),
            new BookingMapper(), registry);
  }

  @Test
  void createLocksEquipmentBeforeOverlapCheck() {
    Instant start = Instant.now().plusSeconds(3600);
    Instant end = start.plusSeconds(7200);
    User owner = user("stu", "STUDENT");
    Equipment item = item(7L, "OSC-001");
    when(users.findByUsername("stu")).thenReturn(Optional.of(owner));
    when(equipment.findWithLockById(7L)).thenReturn(Optional.of(item));
    when(bookings.existsOverlap(any(), anyCollection(), any(), any())).thenReturn(false);
    when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

    var res = service.create("stu", new BookingCreateRequest(7L, start, end, "lab work"));

    assertEquals(BookingStatus.PENDING, res.status());
    assertEquals("OSC-001", res.equipmentCode());
    var order = org.mockito.Mockito.inOrder(equipment, bookings);
    order.verify(equipment).findWithLockById(7L);
    order.verify(bookings).existsOverlap(any(), anyCollection(), any(), any());
  }

  @Test
  void createOverlappingConfirmedBookingThrowsConflict() {
    Instant start = Instant.now().plusSeconds(3600);
    Instant end = start.plusSeconds(7200);
    when(users.findByUsername("stu")).thenReturn(Optional.of(user("stu", "STUDENT")));
    when(equipment.findWithLockById(7L)).thenReturn(Optional.of(item(7L, "OSC-001")));
    when(bookings.existsOverlap(any(), anyCollection(), any(), any())).thenReturn(true);

    assertThrows(
        ConflictException.class,
        () -> service.create("stu", new BookingCreateRequest(7L, start, end, "lab work")));
    verify(bookings, never()).save(any());
    assertEquals(1.0, registry.counter("booking.conflicts").count());
  }

  @Test
  void createEndBeforeStartThrows() {
    Instant start = Instant.now().plusSeconds(7200);
    Instant end = Instant.now().plusSeconds(3600);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.create("stu", new BookingCreateRequest(7L, start, end, "lab work")));
  }

  @Test
  void createZeroDurationThrows() {
    Instant point = Instant.now().plusSeconds(3600);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.create("stu", new BookingCreateRequest(7L, point, point, "lab work")));
  }

  @Test
  void createInPastThrows() {
    Instant start = Instant.now().minusSeconds(7200);
    Instant end = Instant.now().minusSeconds(3600);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.create("stu", new BookingCreateRequest(7L, start, end, "lab work")));
  }

  @Test
  void createMaintenanceEquipmentThrowsConflict() {
    Instant start = Instant.now().plusSeconds(3600);
    Instant end = start.plusSeconds(3600);
    Equipment broken = item(7L, "OSC-001");
    broken.setCurrentStatus(EquipmentStatus.MAINTENANCE);
    when(users.findByUsername("stu")).thenReturn(Optional.of(user("stu", "STUDENT")));
    when(equipment.findWithLockById(7L)).thenReturn(Optional.of(broken));

    assertThrows(
        ConflictException.class,
        () -> service.create("stu", new BookingCreateRequest(7L, start, end, "lab work")));
  }

  @Test
  void createOutOfServiceEquipmentThrowsConflict() {
    Instant start = Instant.now().plusSeconds(3600);
    Instant end = start.plusSeconds(3600);
    Equipment retired = item(7L, "OSC-001");
    retired.setMaintenanceStatus(MaintenanceStatus.OUT_OF_SERVICE);
    when(users.findByUsername("stu")).thenReturn(Optional.of(user("stu", "STUDENT")));
    when(equipment.findWithLockById(7L)).thenReturn(Optional.of(retired));

    assertThrows(
        ConflictException.class,
        () -> service.create("stu", new BookingCreateRequest(7L, start, end, "lab work")));
  }

  @Test
  void createMissingEquipmentThrowsNotFound() {
    Instant start = Instant.now().plusSeconds(3600);
    when(users.findByUsername("stu")).thenReturn(Optional.of(user("stu", "STUDENT")));
    when(equipment.findWithLockById(77L)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () ->
            service.create(
                "stu", new BookingCreateRequest(77L, start, start.plusSeconds(3600), "lab work")));
  }

  @Test
  void getOtherUsersBookingThrowsForbidden() {
    Booking b = ownedBooking(1L, "stuA");
    when(users.findByUsername("stuB")).thenReturn(Optional.of(user("stuB", "STUDENT")));
    when(bookings.findById(1L)).thenReturn(Optional.of(b));

    assertThrows(AccessDeniedException.class, () -> service.get("stuB", 1L));
  }

  @Test
  void cancelByNonOwnerThrowsForbidden() {
    Booking b = ownedBooking(1L, "stuA");
    when(users.findByUsername("stuB")).thenReturn(Optional.of(user("stuB", "STUDENT")));
    when(bookings.findById(1L)).thenReturn(Optional.of(b));

    assertThrows(AccessDeniedException.class, () -> service.cancel("stuB", 1L));
  }

  @Test
  void cancelCompletedThrowsConflict() {
    Booking b = ownedBooking(1L, "stuA");
    b.setStatus(BookingStatus.COMPLETED);
    when(users.findByUsername("stuA")).thenReturn(Optional.of(user("stuA", "STUDENT")));
    when(bookings.findById(1L)).thenReturn(Optional.of(b));

    assertThrows(ConflictException.class, () -> service.cancel("stuA", 1L));
  }

  @Test
  void confirmRechecksOverlap() {
    Booking b = ownedBooking(1L, "stuA");
    Equipment item = item(7L, "OSC-001");
    b.setEquipment(item);
    when(bookings.findById(1L)).thenReturn(Optional.of(b));
    when(equipment.findWithLockById(7L)).thenReturn(Optional.of(item));
    when(bookings.existsOverlap(any(), anyCollection(), any(), any())).thenReturn(true);

    assertThrows(ConflictException.class, () -> service.confirm("stf", 1L));
  }

  @Test
  void confirmNonPendingThrowsConflict() {
    Booking b = ownedBooking(1L, "stuA");
    b.setStatus(BookingStatus.CANCELLED);
    when(bookings.findById(1L)).thenReturn(Optional.of(b));

    assertThrows(ConflictException.class, () -> service.confirm("stf", 1L));
  }

  @Test
  void rejectNonPendingThrowsConflict() {
    Booking b = ownedBooking(1L, "stuA");
    b.setStatus(BookingStatus.CONFIRMED);
    when(bookings.findById(1L)).thenReturn(Optional.of(b));

    assertThrows(ConflictException.class, () -> service.reject("stf", 1L));
  }

  private static User user(String username, String... roleNames) {
    User u = new User();
    u.setUsername(username);
    u.setEmail(username + "@example.com");
    u.setPasswordHash("$2a$10$testhashfortests0000000000000000000000000000");
    for (String r : roleNames) {
      u.getRoles().add(new Role(r, r));
    }
    return u;
  }

  private static Equipment item(Long id, String code) {
    Equipment e = new Equipment();
    e.setEquipmentCode(code);
    e.setName("Oscilloscope");
    e.setCategory("Electronics");
    try {
      var idField = Equipment.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(e, id);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return e;
  }

  private static Booking ownedBooking(Long id, String ownerName) {
    Booking b = new Booking();
    b.setOwner(user(ownerName, "STUDENT"));
    b.setEquipment(item(7L, "OSC-001"));
    Instant start = Instant.now().plusSeconds(3600);
    b.setStartTime(start);
    b.setEndTime(start.plusSeconds(3600));
    b.setPurpose("lab work");
    b.setStatus(BookingStatus.PENDING);
    try {
      var idField = Booking.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(b, id);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return b;
  }
}
