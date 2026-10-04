package com.labmarket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labmarket.entity.Alert;
import com.labmarket.entity.AlertStatus;
import com.labmarket.entity.AlertType;
import com.labmarket.entity.Booking;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.User;
import com.labmarket.repository.AlertRepository;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Pure unit tests for detection with a fixed clock (no waiting, no Spring). */
@ExtendWith(MockitoExtension.class)
class OverdueServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

  @Mock private BookingRepository bookings;
  @Mock private EquipmentRepository equipment;
  @Mock private AlertRepository alerts;
  @Mock private AuditService audit;

  private OverdueService service;
  private SimpleMeterRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    service =
        new OverdueService(
            bookings, equipment, alerts, audit, Clock.fixed(NOW, ZoneOffset.UTC),
            registry);
  }

  @Test
  void pastEndConfirmedBookingBecomesOverdueWithAlertAndAudit() {
    Booking b = booking(11L, BookingStatus.CONFIRMED, NOW.minusSeconds(3600));
    Equipment item = b.getEquipment();
    when(bookings.findByStatusInAndEndTimeBefore(any(), any())).thenReturn(List.of(b));
    when(equipment.findWithLockById(5L)).thenReturn(Optional.of(item));
    when(alerts.existsByBookingIdAndTypeAndStatus(11L, AlertType.OVERDUE, AlertStatus.OPEN))
        .thenReturn(false);

    assertEquals(1, service.detectOverdue());

    assertEquals(BookingStatus.OVERDUE, b.getStatus());
    assertEquals(1.0, registry.counter("overdue.detected").count());
    assertEquals(EquipmentStatus.OVERDUE, item.getCurrentStatus());
    ArgumentCaptor<Alert> alert = ArgumentCaptor.forClass(Alert.class);
    verify(alerts).save(alert.capture());
    assertEquals(AlertType.OVERDUE, alert.getValue().getType());
    assertEquals(AlertStatus.OPEN, alert.getValue().getStatus());
    verify(audit).log(any(), any(), any(), any(), any());
  }

  @Test
  void checkedInPastEndIsAlsoDetected() {
    Booking b = booking(12L, BookingStatus.CHECKED_IN, NOW.minusSeconds(60));
    when(bookings.findByStatusInAndEndTimeBefore(any(), any())).thenReturn(List.of(b));
    when(equipment.findWithLockById(5L)).thenReturn(Optional.of(b.getEquipment()));
    when(alerts.existsByBookingIdAndTypeAndStatus(12L, AlertType.OVERDUE, AlertStatus.OPEN))
        .thenReturn(false);

    assertEquals(1, service.detectOverdue());
    assertEquals(BookingStatus.OVERDUE, b.getStatus());
  }

  @Test
  void noCandidatesIsNoop() {
    when(bookings.findByStatusInAndEndTimeBefore(any(), any())).thenReturn(List.of());

    assertEquals(0, service.detectOverdue());
    verify(alerts, never()).save(any());
    verify(audit, never()).log(any(), any(), any(), any(), any());
  }

  @Test
  void existingOpenAlertIsNotDuplicated() {
    Booking b = booking(13L, BookingStatus.CONFIRMED, NOW.minusSeconds(60));
    when(bookings.findByStatusInAndEndTimeBefore(any(), any())).thenReturn(List.of(b));
    when(equipment.findWithLockById(5L)).thenReturn(Optional.of(b.getEquipment()));
    when(alerts.existsByBookingIdAndTypeAndStatus(13L, AlertType.OVERDUE, AlertStatus.OPEN))
        .thenReturn(true);

    assertEquals(1, service.detectOverdue());
    assertEquals(BookingStatus.OVERDUE, b.getStatus());
    verify(alerts, never()).save(any());
  }

  @Test
  void sensorOfflineEquipmentKeepsItsStatus() {
    Booking b = booking(15L, BookingStatus.CONFIRMED, NOW.minusSeconds(60));
    b.getEquipment().setCurrentStatus(EquipmentStatus.SENSOR_OFFLINE);
    when(bookings.findByStatusInAndEndTimeBefore(any(), any())).thenReturn(List.of(b));
    when(equipment.findWithLockById(5L)).thenReturn(Optional.of(b.getEquipment()));
    when(alerts.existsByBookingIdAndTypeAndStatus(15L, AlertType.OVERDUE, AlertStatus.OPEN))
        .thenReturn(false);

    assertEquals(1, service.detectOverdue());
    assertEquals(EquipmentStatus.SENSOR_OFFLINE, b.getEquipment().getCurrentStatus());
    verify(alerts).save(any()); // the alert is still raised
  }

  @Test
  void maintenanceEquipmentKeepsItsStatus() {
    Booking b = booking(14L, BookingStatus.CONFIRMED, NOW.minusSeconds(60));
    b.getEquipment().setCurrentStatus(EquipmentStatus.MAINTENANCE);
    when(bookings.findByStatusInAndEndTimeBefore(any(), any())).thenReturn(List.of(b));
    when(equipment.findWithLockById(5L)).thenReturn(Optional.of(b.getEquipment()));
    when(alerts.existsByBookingIdAndTypeAndStatus(14L, AlertType.OVERDUE, AlertStatus.OPEN))
        .thenReturn(false);

    assertEquals(1, service.detectOverdue());
    assertEquals(EquipmentStatus.MAINTENANCE, b.getEquipment().getCurrentStatus());
    verify(alerts).save(any()); // the alert is still raised
  }

  private static Booking booking(Long id, BookingStatus status, Instant end) {
    Booking b = new Booking();
    b.setOwner(new User());
    Equipment e = new Equipment();
    e.setEquipmentCode("OSC-001");
    e.setName("Oscilloscope");
    e.setCategory("Electronics");
    e.setCurrentStatus(EquipmentStatus.IN_USE);
    e.setMaintenanceStatus(MaintenanceStatus.OPERATIONAL);
    b.setEquipment(e);
    b.setStartTime(end.minusSeconds(3600));
    b.setEndTime(end);
    b.setPurpose("lab");
    b.setStatus(status);
    try {
      var idField = Booking.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(b, id);
      var eqId = Equipment.class.getDeclaredField("id");
      eqId.setAccessible(true);
      eqId.set(e, 5L);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return b;
  }
}
