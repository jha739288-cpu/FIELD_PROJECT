package com.labmarket.prediction;

import com.labmarket.entity.Booking;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.UsageSession;
import java.time.Instant;
import java.util.List;

/**
 * Pluggable availability predictor. The default is an explainable empirical
 * baseline; a future ML model replaces it by implementing this interface and
 * registering under a new {@code method} name — no controller changes needed.
 */
public interface AvailabilityPredictor {

  /** Method key exposed as {@code ?method=} (e.g. {@code empirical}, {@code naive}). */
  String method();

  /**
   * Predicts every slot. Implementations must be pure functions of the context
   * (no database access) so they stay unit-testable and backtestable.
   */
  List<SlotPrediction> predict(PredictionContext context);

  /** Everything a predictor may use: the item, the slots, future bookings and history. */
  record PredictionContext(
      Equipment equipment,
      List<Slot> slots,
      /** Blocking bookings overlapping the prediction window (deterministic layer). */
      List<Booking> futureBookings,
      /** Historical bookings in [now − lookbackDays, now) (COMPLETED/CONFIRMED/CHECKED_IN). */
      List<Booking> historyBookings,
      /** Historical usage sessions in [now − lookbackDays, now). */
      List<UsageSession> historySessions,
      /** Reference instant: real now for forecasts, the slot start when backtesting. */
      Instant now,
      /** Lookback span in days; weeks = lookbackDays ÷ 7. */
      int lookbackDays) {}

  /** One future slot under evaluation. */
  record Slot(Instant start, Instant end) {}

  /** One forecast with its probability, confidence and contributing factors. */
  record SlotPrediction(
      Instant start,
      Instant end,
      com.labmarket.entity.PredictedStatus status,
      /** P(available) in [0, 1]. */
      double probabilityAvailable,
      /** 0..1: sample-size driven; low when history is thin. */
      double confidence,
      List<Factor> factors) {}

  /** One named contributor, e.g. {dow_hour_occupancy, 0.33, "…"} — the explanation. */
  record Factor(String name, double value, String detail) {}
}
