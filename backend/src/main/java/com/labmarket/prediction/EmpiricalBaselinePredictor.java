package com.labmarket.prediction;

import com.labmarket.entity.Booking;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.PredictedStatus;
import com.labmarket.entity.UsageSession;
import com.labmarket.prediction.AvailabilityPredictor.Factor;
import com.labmarket.prediction.AvailabilityPredictor.PredictionContext;
import com.labmarket.prediction.AvailabilityPredictor.SlotPrediction;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Explainable empirical baseline (method {@code empirical}).
 *
 * <p>For each slot, in order:
 * <ol>
 *   <li><b>Deterministic layer.</b> A blocking future booking covering the slot, or an
 *       unbookable equipment state → UNAVAILABLE, p=0, confidence=1.</li>
 *   <li><b>Historical layer.</b> Over the lookback window (default 28 days = 4 weeks):
 *       <ul>
 *         <li>{@code busyRate} = weeks in which any historical booking overlapped the
 *             slot's (day-of-week, hour) block ÷ total weeks.</li>
 *         <li>{@code pBucket} = Laplace-smoothed availability
 *             {@code (freeWeeks + 1) / (weeks + 2)} — never 0/1 on thin data.</li>
 *         <li>{@code U} = trailing utilization (usage seconds ÷ window capacity).</li>
 *         <li>{@code p = 0.7 × pBucket + 0.3 × (1 − U)} (documented linear blend).</li>
 *         <li>Status: p ≥ 0.65 AVAILABLE, p ≥ 0.35 LIMITED, else UNAVAILABLE.</li>
 *         <li>Confidence = weeks containing any history ÷ total weeks (0.1 when the
 *             item has no history at all, with p pinned to 0.5).</li>
 *       </ul>
 *   </li>
 * </ol>
 * All quantities use UTC. This is a forecast aid, never a booking guarantee.
 */
@Component("empirical")
public class EmpiricalBaselinePredictor implements AvailabilityPredictor {

  /** Blend weights: bucket rate vs. trailing-utilization prior. */
  static final double W_BUCKET = 0.85;

  static final double W_UTILIZATION = 0.15;

  static final double P_AVAILABLE = 0.65;

  static final double P_LIMITED = 0.35;

  /** Booking states that count as historical occupancy. */
  static final Set<BookingStatus> HISTORY_STATUSES =
      EnumSet.of(BookingStatus.COMPLETED, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

  @Override
  public String method() {
    return "empirical";
  }

  @Override
  public List<SlotPrediction> predict(PredictionContext ctx) {
    List<SlotPrediction> out = new ArrayList<>();
    for (var slot : ctx.slots()) {
      out.add(predictSlot(ctx, slot.start(), slot.end()));
    }
    return out;
  }

  private SlotPrediction predictSlot(PredictionContext ctx, Instant start, Instant end) {
    boolean booked =
        ctx.futureBookings().stream()
            .anyMatch(b -> b.getStartTime().isBefore(end) && b.getEndTime().isAfter(start));
    if (booked) {
      return new SlotPrediction(
          start, end, PredictedStatus.UNAVAILABLE, 0.0, 1.0,
          List.of(new Factor("existing_booking", 1.0, "A confirmed booking covers this slot")));
    }
    var item = ctx.equipment();
    if (item.getMaintenanceStatus() != MaintenanceStatus.OPERATIONAL
        || item.getCurrentStatus() == EquipmentStatus.MAINTENANCE
        || item.getCurrentStatus() == EquipmentStatus.SENSOR_OFFLINE) {
      return new SlotPrediction(
          start, end, PredictedStatus.UNAVAILABLE, 0.0, 1.0,
          List.of(
              new Factor(
                  "equipment_state", 0.0,
                  "Item is " + item.getCurrentStatus() + " / " + item.getMaintenanceStatus())));
    }

    int totalWeeks = lookbackWeeks(ctx);
    if (ctx.historyBookings().isEmpty() && ctx.historySessions().isEmpty()) {
      return new SlotPrediction(
          start, end, PredictedStatus.LIMITED, 0.5, 0.1,
          List.of(
              new Factor(
                  "insufficient_history", 0.0,
                  "No historical bookings or usage in the lookback window — neutral forecast")));
    }
    int busyWeeks = busyWeeks(ctx, start);
    double busyRate = totalWeeks == 0 ? 0.0 : (double) busyWeeks / totalWeeks;
    double pBucket = ((totalWeeks - busyWeeks) + 1.0) / (totalWeeks + 2.0);
    double utilization = trailingUtilization(ctx);
    double p = W_BUCKET * pBucket + W_UTILIZATION * (1.0 - utilization);
    p = Math.min(1.0, Math.max(0.0, p));
    PredictedStatus status =
        p >= P_AVAILABLE ? PredictedStatus.AVAILABLE : p >= P_LIMITED ? PredictedStatus.LIMITED
            : PredictedStatus.UNAVAILABLE;
    double confidence = Math.min(1.0, Math.max(0.1, (double) weeksWithData(ctx) / Math.max(1, totalWeeks)));
    return new SlotPrediction(
        start, end, status, round3(p), round3(confidence),
        List.of(
            new Factor("dow_hour_occupancy", round3(busyRate),
                busyWeeks + " of " + totalWeeks + " weeks booked at "
                    + dayHour(start)),
            new Factor("trailing_utilization", round3(utilization),
                "Usage ÷ capacity over the lookback window"),
            new Factor("sample_weeks", weeksWithData(ctx),
                "Weeks containing any history (blend " + W_BUCKET + "/" + W_UTILIZATION + ")")));
  }

  /** Whole weeks in the lookback span (lookback days ÷ 7, minimum 1). */
  private static int lookbackWeeks(PredictionContext ctx) {
    return Math.max(1, ctx.lookbackDays() / 7);
  }

  private static int busyWeeks(PredictionContext ctx, Instant slotStart) {
    DayOfWeek dow = slotStart.atZone(ZoneOffset.UTC).getDayOfWeek();
    int hour = slotStart.atZone(ZoneOffset.UTC).getHour();
    Instant histEnd = ctx.now();
    int weeks = lookbackWeeks(ctx);
    int busy = 0;
    for (int w = 0; w < weeks; w++) {
      Instant weekStart = histEnd.minusSeconds(7L * 86400 * (w + 1));
      Instant block = blockFor(weekStart, dow, hour);
      Instant blockEnd = block.plusSeconds(3600);
      boolean hit =
          ctx.historyBookings().stream()
              .anyMatch(b -> b.getStartTime().isBefore(blockEnd) && b.getEndTime().isAfter(block));
      if (hit) {
        busy++;
      }
    }
    return busy;
  }

  /** The (dow, hour) hour-block inside the week starting at {@code weekStart}. */
  static Instant blockFor(Instant weekStart, DayOfWeek dow, int hour) {
    var start = weekStart.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    int offset = (dow.getValue() - start.atZone(ZoneOffset.UTC).getDayOfWeek().getValue() + 7) % 7;
    return start.plusSeconds((long) offset * 86400 + (long) hour * 3600);
  }

  private static int weeksWithData(PredictionContext ctx) {
    Instant histEnd = ctx.now();
    int weeks = lookbackWeeks(ctx);
    int withData = 0;
    for (int w = 0; w < weeks; w++) {
      Instant from = histEnd.minusSeconds(7L * 86400 * (w + 1));
      Instant to = histEnd.minusSeconds(7L * 86400 * w);
      boolean hit =
          ctx.historyBookings().stream()
                  .anyMatch(b -> b.getStartTime().isBefore(to) && b.getEndTime().isAfter(from))
              || ctx.historySessions().stream()
                  .anyMatch(
                      s ->
                          s.getStartedAt().isBefore(to)
                              && (s.getEndedAt() == null || s.getEndedAt().isAfter(from)));
      if (hit) {
        withData++;
      }
    }
    return withData;
  }

  private static double trailingUtilization(PredictionContext ctx) {
    Instant histEnd = ctx.now();
    long capacity = Math.max(1, (long) ctx.lookbackDays() * 86400);
    long used = 0;
    Instant from = histEnd.minusSeconds((long) ctx.lookbackDays() * 86400);
    for (UsageSession s : ctx.historySessions()) {
      Instant s0 = s.getStartedAt().isBefore(from) ? from : s.getStartedAt();
      Instant e1 = s.getEndedAt() == null ? histEnd : s.getEndedAt();
      if (e1.isAfter(histEnd)) {
        e1 = histEnd;
      }
      if (e1.isAfter(s0)) {
        used += Duration.between(s0, e1).getSeconds();
      }
    }
    return Math.min(1.0, (double) used / capacity);
  }

  private static String dayHour(Instant slotStart) {
    var z = slotStart.atZone(ZoneOffset.UTC);
    return z.getDayOfWeek() + " " + z.getHour() + ":00 UTC";
  }

  private static double round3(double v) {
    return Math.round(v * 1000.0) / 1000.0;
  }
}
