package com.labmarket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.prediction.EmpiricalBaselinePredictor;
import com.labmarket.prediction.NaiveCurrentPredictor;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.UsageSessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Service orchestration tests: method selection, window validation, evaluation guard. */
@ExtendWith(MockitoExtension.class)
class PredictionServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

  @Mock private EquipmentRepository equipment;
  @Mock private BookingRepository bookings;
  @Mock private UsageSessionRepository sessions;

  private PredictionService service;

  @BeforeEach
  void setUp() {
    service =
        new PredictionService(
            equipment, bookings, sessions,
            List.of(new EmpiricalBaselinePredictor(), new NaiveCurrentPredictor()),
            Clock.fixed(NOW, ZoneOffset.UTC), 28, "empirical", new SimpleMeterRegistry());
  }

  @Test
  void unknownMethodIsRejected() {
    when(equipment.findById(9L)).thenReturn(Optional.of(item()));
    Instant from = NOW.plusSeconds(3600);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.predict(9L, from, from.plusSeconds(3600), 60, "neural-net"));
  }

  @Test
  void naiveMethodIsSelectable() {
    when(equipment.findById(9L)).thenReturn(Optional.of(item()));
    when(bookings.findByEquipmentIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(
            any(), any(), any(), any()))
        .thenReturn(List.of());
    when(sessions.findOverlappingForEquipment(any(), any(), any())).thenReturn(List.of());
    Instant from = NOW.plusSeconds(3600);

    var res = service.predict(9L, from, from.plusSeconds(3600), 60, "naive");

    assertEquals("naive", res.method());
    assertEquals(1, res.slots().size());
    assertEquals("AVAILABLE", res.slots().get(0).predictedStatus().name());
  }

  @Test
  void badWindowsAreRejected() {
    Instant from = NOW.plusSeconds(3600);
    assertThrows(
        IllegalArgumentException.class, () -> service.predict(9L, from, from, 60, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.predict(9L, from, from.plusSeconds(3600), 5, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.predict(9L, from, from.plusSeconds(100 * 86400L), 60, null));
  }

  @Test
  void missingEquipmentIsNotFound() {
    when(equipment.findById(9L)).thenReturn(Optional.empty());
    Instant from = NOW.plusSeconds(3600);

    assertThrows(
        com.labmarket.exception.ResourceNotFoundException.class,
        () -> service.predict(9L, from, from.plusSeconds(3600), 60, null));
  }

  @Test
  void evaluationRefusesWithoutHistory() {
    when(equipment.findById(9L)).thenReturn(Optional.of(item()));
    when(bookings.findByEquipmentIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(
            any(), any(), any(), any()))
        .thenReturn(List.of());
    when(sessions.findOverlappingForEquipment(any(), any(), any())).thenReturn(List.of());
    Instant from = NOW.minusSeconds(7 * 86400);

    assertThrows(
        IllegalArgumentException.class, () -> service.evaluate(9L, from, NOW, 60));
  }

  private static Equipment item() {
    Equipment e = new Equipment();
    e.setEquipmentCode("OSC-001");
    e.setName("Scope");
    e.setCategory("Electronics");
    e.setCurrentStatus(EquipmentStatus.AVAILABLE);
    e.setMaintenanceStatus(MaintenanceStatus.OPERATIONAL);
    try {
      var idField = Equipment.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(e, 9L);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return e;
  }
}
