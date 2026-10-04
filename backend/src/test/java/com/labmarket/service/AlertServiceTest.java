package com.labmarket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labmarket.dto.ResolveAlertRequest;
import com.labmarket.entity.Alert;
import com.labmarket.entity.AlertStatus;
import com.labmarket.entity.AlertType;
import com.labmarket.entity.Booking;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.Role;
import com.labmarket.entity.UsageSession;
import com.labmarket.entity.User;
import com.labmarket.exception.ConflictException;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.mapper.AlertMapper;
import com.labmarket.repository.AlertRepository;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.UsageSessionRepository;
import com.labmarket.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/** Pure unit tests for alert resolution (no Spring context). */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

  @Mock private AlertRepository alerts;
  @Mock private BookingRepository bookings;
  @Mock private EquipmentRepository equipment;
  @Mock private UsageSessionRepository sessions;
  @Mock private UserRepository users;
  @Mock private AuditService audit;

  private AlertService service;

  @BeforeEach
  void setUp() {
    service =
        new AlertService(
            alerts, bookings, equipment, sessions, users, audit, new AlertMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void resolveWithOpenSessionCompletesBookingAndFreesEquipment() {
    Fixture f = overdueFixture(true);
    when(alerts.findById(100L)).thenReturn(Optional.of(f.alert));
    when(users.findByUsername("stf")).thenReturn(Optional.of(staff()));
    when(sessions.findByBookingId(11L)).thenReturn(Optional.of(f.session));
    when(equipment.findWithLockById(5L)).thenReturn(Optional.of(f.item));

    var res = service.resolve("stf", 100L, new ResolveAlertRequest("collected"));

    assertEquals(AlertStatus.RESOLVED, f.alert.getStatus());
    assertEquals("collected", f.alert.getResolutionNote());
    assertEquals(BookingStatus.COMPLETED, f.booking.getStatus());
    assertEquals(EquipmentStatus.AVAILABLE, f.item.getCurrentStatus());
    assertEquals(7200L, f.session.getDurationSeconds());
    assertEquals("COMPLETED", f.session.getStatus().name());
    assertEquals("stf", res.resolvedByUsername());
    verify(audit, org.mockito.Mockito.times(2)).log(any(), any(), any(), any(), any());
  }

  @Test
  void resolveWithoutSessionCancelsBooking() {
    Fixture f = overdueFixture(false);
    when(alerts.findById(100L)).thenReturn(Optional.of(f.alert));
    when(users.findByUsername("stf")).thenReturn(Optional.of(staff()));
    when(sessions.findByBookingId(11L)).thenReturn(Optional.empty());
    when(equipment.findWithLockById(5L)).thenReturn(Optional.of(f.item));

    service.resolve("stf", 100L, new ResolveAlertRequest(null));

    assertEquals(BookingStatus.CANCELLED, f.booking.getStatus());
    assertEquals(EquipmentStatus.AVAILABLE, f.item.getCurrentStatus());
  }

  @Test
  void resolveKeepsStaffSetMaintenanceState() {
    Fixture f = overdueFixture(false);
    f.item.setCurrentStatus(EquipmentStatus.MAINTENANCE);
    when(alerts.findById(100L)).thenReturn(Optional.of(f.alert));
    when(users.findByUsername("stf")).thenReturn(Optional.of(staff()));
    when(sessions.findByBookingId(11L)).thenReturn(Optional.empty());
    when(equipment.findWithLockById(5L)).thenReturn(Optional.of(f.item));

    service.resolve("stf", 100L, new ResolveAlertRequest(null));

    assertEquals(EquipmentStatus.MAINTENANCE, f.item.getCurrentStatus());
  }

  @Test
  void resolveAlreadyResolvedIsConflict() {
    Fixture f = overdueFixture(false);
    f.alert.setStatus(AlertStatus.RESOLVED);
    when(alerts.findById(100L)).thenReturn(Optional.of(f.alert));

    assertThrows(ConflictException.class, () -> service.resolve("stf", 100L, null));
    verify(sessions, never()).findByBookingId(any());
  }

  @Test
  void resolveMissingIsNotFound() {
    when(alerts.findById(100L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> service.resolve("stf", 100L, null));
  }

  @Test
  void resolveByNonStaffIsForbidden() {
    Fixture f = overdueFixture(false);
    when(alerts.findById(100L)).thenReturn(Optional.of(f.alert));
    when(users.findByUsername("stu")).thenReturn(Optional.of(student()));

    assertThrows(AccessDeniedException.class, () -> service.resolve("stu", 100L, null));
  }

  private record Fixture(Alert alert, Booking booking, Equipment item, UsageSession session) {}

  private static Fixture overdueFixture(boolean withSession) {
    User owner = new User();
    owner.setUsername("stu");
    Equipment item = new Equipment();
    item.setEquipmentCode("OSC-001");
    item.setName("Oscilloscope");
    item.setCategory("Electronics");
    item.setCurrentStatus(EquipmentStatus.OVERDUE);
    item.setMaintenanceStatus(MaintenanceStatus.OPERATIONAL);
    Booking booking = new Booking();
    booking.setOwner(owner);
    booking.setEquipment(item);
    booking.setStartTime(NOW.minusSeconds(7200));
    booking.setEndTime(NOW.minusSeconds(3600));
    booking.setPurpose("lab");
    booking.setStatus(BookingStatus.OVERDUE);
    Alert alert = new Alert();
    alert.setType(AlertType.OVERDUE);
    alert.setBooking(booking);
    alert.setEquipment(item);
    alert.setStatus(AlertStatus.OPEN);
    alert.setMessage("overdue");
    UsageSession session = null;
    if (withSession) {
      session = new UsageSession();
      session.setBooking(booking);
      session.setEquipment(item);
      session.setUser(owner);
      session.setStartedAt(NOW.minusSeconds(7200));
    }
    try {
      var bookingId = Booking.class.getDeclaredField("id");
      bookingId.setAccessible(true);
      bookingId.set(booking, 11L);
      var equipmentId = Equipment.class.getDeclaredField("id");
      equipmentId.setAccessible(true);
      equipmentId.set(item, 5L);
      var alertId = Alert.class.getDeclaredField("id");
      alertId.setAccessible(true);
      alertId.set(alert, 100L);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    return new Fixture(alert, booking, item, session);
  }

  private static User staff() {
    User u = new User();
    u.setUsername("stf");
    u.getRoles().add(new Role("LAB_STAFF", "x"));
    return u;
  }

  private static User student() {
    User u = new User();
    u.setUsername("stu");
    u.getRoles().add(new Role("STUDENT", "x"));
    return u;
  }
}
