package com.labmarket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labmarket.dto.SensorEventRequest;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.SensorEvent;
import com.labmarket.entity.SensorStatus;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.exception.SensorUnauthorizedException;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.SensorEventRepository;
import com.labmarket.security.SensorRateLimiter;
import java.math.BigDecimal;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Pure unit tests for sensor validation and the status mapping (no Spring context). */
@ExtendWith(MockitoExtension.class)
class SensorServiceTest {

  @Mock private EquipmentRepository equipment;
  @Mock private SensorEventRepository events;
  @Mock private BookingRepository bookings;
  @Mock private AuditService audit;

  private SensorService service;

  @BeforeEach
  void setUp() {
    service =
        new SensorService(
            equipment, events, bookings, audit,
            new SensorRateLimiter(1000, 60, Clock.systemUTC()), new SimpleMeterRegistry());
  }

  @Test
  void inUseWithDrawFlipsAvailable() {
    Equipment item = item(EquipmentStatus.AVAILABLE);
    wire(item);

    var res = service.ingest("dev-key", event(SensorStatus.IN_USE, "1.80", Instant.now()));

    assertEquals(EquipmentStatus.IN_USE, res.appliedEquipmentStatus());
    assertFalse(res.stale());
  }

  @Test
  void inUseWithZeroDrawIsIgnoredAsContradictory() {
    Equipment item = item(EquipmentStatus.AVAILABLE);
    wire(item);

    var res = service.ingest("dev-key", event(SensorStatus.IN_USE, "0.00", Instant.now()));

    assertEquals(EquipmentStatus.AVAILABLE, item.getCurrentStatus());
    assertEquals(EquipmentStatus.AVAILABLE, res.appliedEquipmentStatus());
  }

  @Test
  void idleReturnsToAvailableWithoutLiveSession() {
    Equipment item = item(EquipmentStatus.IN_USE);
    wire(item);
    when(bookings.existsByEquipmentIdAndStatus(5L, BookingStatus.CHECKED_IN)).thenReturn(false);

    var res = service.ingest("dev-key", event(SensorStatus.IDLE, "0.00", Instant.now()));

    assertEquals(EquipmentStatus.AVAILABLE, res.appliedEquipmentStatus());
  }

  @Test
  void idleKeepsInUseWithLiveSession() {
    Equipment item = item(EquipmentStatus.IN_USE);
    wire(item);
    when(bookings.existsByEquipmentIdAndStatus(5L, BookingStatus.CHECKED_IN)).thenReturn(true);

    var res = service.ingest("dev-key", event(SensorStatus.IDLE, "0.00", Instant.now()));

    assertEquals(EquipmentStatus.IN_USE, item.getCurrentStatus());
  }

  @Test
  void offlineBecomesSensorOffline() {
    Equipment item = item(EquipmentStatus.AVAILABLE);
    wire(item);

    var res = service.ingest("dev-key", event(SensorStatus.OFFLINE, null, Instant.now()));

    assertEquals(EquipmentStatus.SENSOR_OFFLINE, res.appliedEquipmentStatus());
  }

  @Test
  void faultBecomesMaintenance() {
    Equipment item = item(EquipmentStatus.AVAILABLE);
    wire(item);

    var res = service.ingest("dev-key", event(SensorStatus.FAULT, "9.99", Instant.now()));

    assertEquals(EquipmentStatus.MAINTENANCE, res.appliedEquipmentStatus());
  }

  @Test
  void maintenanceIsNeverAutoChanged() {
    Equipment item = item(EquipmentStatus.MAINTENANCE);
    wire(item);

    var res = service.ingest("dev-key", event(SensorStatus.IDLE, "0.00", Instant.now()));

    assertEquals(EquipmentStatus.MAINTENANCE, item.getCurrentStatus());
  }

  @Test
  void unknownCodeIsUnauthorized() {
    // Security: callers without a valid key must not learn which codes exist.
    when(equipment.findByEquipmentCode("NOPE-1")).thenReturn(Optional.empty());

    assertThrows(
        SensorUnauthorizedException.class,
        () -> service.ingest("dev-key", event("NOPE-1", SensorStatus.IDLE, null, Instant.now())));
  }

  @Test
  void wrongKeyIsUnauthorized() {
    Equipment item = item(EquipmentStatus.AVAILABLE);
    when(equipment.findByEquipmentCode("OSC-001")).thenReturn(Optional.of(item));

    assertThrows(
        SensorUnauthorizedException.class,
        () -> service.ingest("wrong-key", event(SensorStatus.IDLE, null, Instant.now())));
  }

  @Test
  void futureTimestampIsRejected() {
    Equipment item = item(EquipmentStatus.AVAILABLE);
    wire(item);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.ingest("dev-key", event(SensorStatus.IDLE, null, Instant.now().plusSeconds(3600))));
  }

  @Test
  void ancientTimestampIsRejected() {
    Equipment item = item(EquipmentStatus.AVAILABLE);
    wire(item);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.ingest(
                "dev-key", event(SensorStatus.IDLE, null, Instant.now().minusSeconds(100_000))));
  }

  @Test
  void oldTimestampIsStoredButFlaggedStale() {
    Equipment item = item(EquipmentStatus.AVAILABLE);
    wire(item);

    var res =
        service.ingest("dev-key", event(SensorStatus.IDLE, null, Instant.now().minusSeconds(600)));

    assertTrue(res.stale());
    ArgumentCaptor<SensorEvent> saved = ArgumentCaptor.forClass(SensorEvent.class);
    verify(events).save(saved.capture());
    assertTrue(saved.getValue().isStale());
  }

  private void wire(Equipment item) {
    item.setSensorKeyHash(sha256("dev-key"));
    when(equipment.findByEquipmentCode("OSC-001")).thenReturn(Optional.of(item));
    when(equipment.findWithLockById(5L)).thenReturn(Optional.of(item));
  }

  private static Equipment item(EquipmentStatus status) {
    Equipment e = new Equipment();
    e.setEquipmentCode("OSC-001");
    e.setName("Oscilloscope");
    e.setCategory("Electronics");
    e.setCurrentStatus(status);
    e.setMaintenanceStatus(MaintenanceStatus.OPERATIONAL);
    try {
      var idField = Equipment.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(e, 5L);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return e;
  }

  private static SensorEventRequest event(SensorStatus status, String amps, Instant at) {
    return event("OSC-001", status, amps, at);
  }

  private static SensorEventRequest event(String code, SensorStatus status, String amps, Instant at) {
    return new SensorEventRequest(
        code, status, amps == null ? null : new BigDecimal(amps), at);
  }

  private static String sha256(String raw) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
