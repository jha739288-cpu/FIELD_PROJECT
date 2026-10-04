package com.labmarket.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.labmarket.entity.Booking;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.PredictedStatus;
import com.labmarket.entity.UsageSession;
import com.labmarket.prediction.AvailabilityPredictor.PredictionContext;
import com.labmarket.prediction.AvailabilityPredictor.Slot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure math tests on synthetic fixtures: they verify the documented formulas,
 * not real-world accuracy (no such claim is made anywhere).
 */
class PredictionMathTest {

  // Monday 2026-09-21 10:00 UTC, shifted exactly 4 weeks out so history weeks
  // (SLOT − 7/14/21/28 days) share its day-of-week and hour.
  private static final Instant SLOT = Instant.parse("2026-09-21T10:00:00Z").plusSeconds(28L * 86400);

  private static final int LOOKBACK = 28;

  private final EmpiricalBaselinePredictor empirical = new EmpiricalBaselinePredictor();
  private final NaiveCurrentPredictor naive = new NaiveCurrentPredictor();

  @Test
  void deterministicBookingOverride() {
    var item = equipment(EquipmentStatus.AVAILABLE);
    Booking future = booking(SLOT, SLOT.plusSeconds(3600), BookingStatus.CONFIRMED);
    var ctx = context(item, List.of(slot()), List.of(future), List.of(), List.of(), SLOT);

    var out = empirical.predict(ctx);

    assertEquals(1, out.size());
    assertEquals(PredictedStatus.UNAVAILABLE, out.get(0).status());
    assertEquals(0.0, out.get(0).probabilityAvailable());
    assertEquals(1.0, out.get(0).confidence());
    assertEquals("existing_booking", out.get(0).factors().get(0).name());
  }

  @Test
  void unbookableStateOverridesHistory() {
    var item = equipment(EquipmentStatus.MAINTENANCE);
    Instant past = SLOT.minusSeconds(7 * 86400);
    var history = List.of(booking(past, past.plusSeconds(3600), BookingStatus.COMPLETED));
    var ctx = context(item, List.of(slot()), List.of(), history, List.of(), SLOT);

    var out = empirical.predict(ctx);

    assertEquals(PredictedStatus.UNAVAILABLE, out.get(0).status());
    assertEquals("equipment_state", out.get(0).factors().get(0).name());
  }

  @Test
  void coldStartIsNeutralWithLowConfidence() {
    var item = equipment(EquipmentStatus.AVAILABLE);
    var ctx = context(item, List.of(slot()), List.of(), List.of(), List.of(), SLOT);

    var out = empirical.predict(ctx);

    assertEquals(PredictedStatus.LIMITED, out.get(0).status());
    assertEquals(0.5, out.get(0).probabilityAvailable());
    assertEquals(0.1, out.get(0).confidence());
  }

  @Test
  void fullyBookedBucketPredictsUnavailable() {
    var item = equipment(EquipmentStatus.AVAILABLE);
    // Same Monday-10:00 slot booked in all 4 lookback weeks.
    var history = new ArrayList<Booking>();
    for (int w = 1; w <= 4; w++) {
      Instant bs = SLOT.minusSeconds((long) w * 7 * 86400);
      history.add(booking(bs, bs.plusSeconds(3600), BookingStatus.COMPLETED));
    }
    var ctx = context(item, List.of(slot()), List.of(), history, List.of(), SLOT);

    var out = empirical.predict(ctx);

    assertEquals(PredictedStatus.UNAVAILABLE, out.get(0).status());
    assertTrue(out.get(0).probabilityAvailable() < 0.35);
    assertTrue(hasFactor(out.get(0), "dow_hour_occupancy"));
    assertEquals(1.0, out.get(0).confidence());
  }

  @Test
  void neverBookedBucketPredictsAvailable() {
    var item = equipment(EquipmentStatus.AVAILABLE);
    // History exists (so not cold start) but always on Fridays, never Mondays.
    var history = new ArrayList<Booking>();
    for (int w = 1; w <= 4; w++) {
      Instant bs = SLOT.minusSeconds((long) w * 7 * 86400).plusSeconds(4 * 86400);
      history.add(booking(bs, bs.plusSeconds(3600), BookingStatus.COMPLETED));
    }
    var ctx = context(item, List.of(slot()), List.of(), history, List.of(), SLOT);

    var out = empirical.predict(ctx);

    assertEquals(PredictedStatus.AVAILABLE, out.get(0).status());
    assertTrue(out.get(0).probabilityAvailable() >= 0.65);
    assertTrue(out.get(0).confidence() >= 0.5);
  }

  @Test
  void naiveBaselineIgnoresHistory() {
    var item = equipment(EquipmentStatus.AVAILABLE);
    Instant past = SLOT.minusSeconds(7 * 86400);
    var history = List.of(booking(past, past.plusSeconds(3600), BookingStatus.COMPLETED));
    var ctx = context(item, List.of(slot()), List.of(), history, List.of(), SLOT);

    var out = naive.predict(ctx);

    assertEquals("naive", naive.method());
    assertEquals(PredictedStatus.AVAILABLE, out.get(0).status());
    assertEquals(0.5, out.get(0).confidence());
  }

  @Test
  void naiveMarksCoveredSlotUnavailable() {
    var item = equipment(EquipmentStatus.AVAILABLE);
    var ctx = context(item, List.of(slot()),
        List.of(booking(SLOT, SLOT.plusSeconds(1800), BookingStatus.CONFIRMED)),
        List.of(), List.of(), SLOT);

    var out = naive.predict(ctx);

    assertEquals(PredictedStatus.UNAVAILABLE, out.get(0).status());
    assertEquals(0.0, out.get(0).probabilityAvailable());
  }

  private static Slot slot() {
    return new Slot(SLOT, SLOT.plusSeconds(3600));
  }

  private static boolean hasFactor(AvailabilityPredictor.SlotPrediction p, String name) {
    return p.factors().stream().anyMatch(f -> f.name().equals(name));
  }

  private static PredictionContext context(
      Equipment item, List<Slot> slots, List<Booking> future, List<Booking> history,
      List<UsageSession> usage, Instant now) {
    return new PredictionContext(item, slots, future, history, usage, now, LOOKBACK);
  }

  private static Equipment equipment(EquipmentStatus status) {
    Equipment e = new Equipment();
    e.setEquipmentCode("OSC-001");
    e.setName("Scope");
    e.setCategory("Electronics");
    e.setCurrentStatus(status);
    e.setMaintenanceStatus(MaintenanceStatus.OPERATIONAL);
    return e;
  }

  private static Booking booking(Instant start, Instant end, BookingStatus status) {
    Booking b = new Booking();
    b.setEquipment(equipment(EquipmentStatus.AVAILABLE));
    b.setStartTime(start);
    b.setEndTime(end);
    b.setPurpose("x");
    b.setStatus(status);
    return b;
  }
}
