package com.labmarket.service;

import com.labmarket.dto.EvaluationResponse;
import com.labmarket.dto.PredictionResponse;
import com.labmarket.dto.PredictionResponse.FactorView;
import com.labmarket.dto.PredictionResponse.SlotForecast;
import com.labmarket.entity.Booking;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.PredictedStatus;
import com.labmarket.entity.UsageSession;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.prediction.AvailabilityPredictor;
import com.labmarket.prediction.AvailabilityPredictor.PredictionContext;
import com.labmarket.prediction.AvailabilityPredictor.Slot;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.UsageSessionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prediction orchestration: fetches data, delegates math to the selected
 * {@link AvailabilityPredictor}, and backtests with {@link PredictionEvaluator}.
 * New models plug in as beans — this class never changes for them.
 */
@Service
public class PredictionService {

  static final Set<BookingStatus> BLOCKING =
      EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.OVERDUE);

  static final Set<BookingStatus> HISTORY =
      EnumSet.of(BookingStatus.COMPLETED, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

  static final int MIN_SLOT_MINUTES = 15;

  static final int MAX_SLOT_MINUTES = 480;

  static final int MAX_SLOTS = 500;

  private static final Duration MAX_WINDOW = Duration.ofDays(93);

  public static final String DISCLAIMER =
      "Prediction, not a guarantee: estimated from historical patterns and current bookings; "
          + "actual availability can differ (maintenance, walk-ins, sensor outages).";

  private final EquipmentRepository equipment;
  private final BookingRepository bookings;
  private final UsageSessionRepository sessions;
  private final Map<String, AvailabilityPredictor> predictors;
  private final Clock clock;
  private final int lookbackDays;
  private final String defaultMethod;
  private final MeterRegistry registry;

  public PredictionService(
      EquipmentRepository equipment,
      BookingRepository bookings,
      UsageSessionRepository sessions,
      List<AvailabilityPredictor> predictors,
      Clock clock,
      @Value("${app.prediction.lookback-days:28}") int lookbackDays,
      @Value("${app.prediction.default-method:empirical}") String defaultMethod,
      MeterRegistry registry) {
    this.equipment = equipment;
    this.bookings = bookings;
    this.sessions = sessions;
    this.predictors =
        predictors.stream().collect(Collectors.toMap(AvailabilityPredictor::method, Function.identity()));
    this.clock = clock;
    this.lookbackDays = lookbackDays;
    this.defaultMethod = defaultMethod;
    this.registry = registry;
  }

  @Transactional(readOnly = true)
  public PredictionResponse predict(
      Long equipmentId, Instant from, Instant to, int slotMinutes, String method) {
    List<Slot> slots = window(from, to, slotMinutes);
    Equipment item = loadEquipment(equipmentId);
    Instant now = clock.instant();
    Instant histFrom = now.minusSeconds((long) lookbackDays * 86400);
    List<Booking> future =
        bookings.findByEquipmentIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(
            equipmentId, BLOCKING, to, from);
    List<Booking> history =
        bookings.findByEquipmentIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(
            equipmentId, HISTORY, now, histFrom);
    List<UsageSession> usage = sessions.findOverlappingForEquipment(equipmentId, histFrom, now);
    var ctx =
        new PredictionContext(item, slots, future, history, usage, now, lookbackDays);
    var predictions = predictor(method).predict(ctx);
    registry.counter("prediction.requests", "method", predictor(method).method()).increment();
    return new PredictionResponse(
        item.getId(), item.getEquipmentCode(), predictor(method).method(), from, to, slotMinutes,
        DISCLAIMER,
        predictions.stream()
            .map(
                p ->
                    new SlotForecast(
                        p.start(), p.end(), p.status(), p.probabilityAvailable(), p.confidence(),
                        p.factors().stream()
                            .map(f -> new FactorView(f.name(), f.value(), f.detail()))
                            .toList()))
            .toList());
  }

  /**
   * Backtests each past slot as-of its start (only data created before the cutoff
   * informs the forecast) against what actually overlapped it. Refuses when the
   * history is too thin to mean anything.
   */
  @Transactional(readOnly = true)
  public EvaluationResponse evaluate(Long equipmentId, Instant from, Instant to, int slotMinutes) {
    List<Slot> slots = window(from, to, slotMinutes);
    Equipment item = loadEquipment(equipmentId);
    Instant histFrom = from.minusSeconds((long) lookbackDays * 86400);
    List<Booking> allHistory =
        bookings.findByEquipmentIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(
            equipmentId, HISTORY, to, histFrom);
    List<UsageSession> allUsage = sessions.findOverlappingForEquipment(equipmentId, histFrom, to);
    if (allHistory.isEmpty() && allUsage.isEmpty()) {
      throw new IllegalArgumentException(
          "Insufficient history for evaluation: no bookings or usage in the lookback window");
    }
    var predictor = predictor("empirical");
    int correct = 0;
    int predictedUnavailable = 0;
    int actualUnavailable = 0;
    int trueUnavailable = 0;
    double brierSum = 0.0;
    int n = 0;
    for (Slot slot : slots) {
      Instant cutoff = slot.start();
      Instant hFrom = cutoff.minusSeconds((long) lookbackDays * 86400);
      List<Booking> knownFuture =
          bookings.findByEquipmentIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(
                  equipmentId, BLOCKING, slot.end(), slot.start()).stream()
              .filter(b -> !b.getCreatedAt().isAfter(cutoff))
              .toList();
      List<Booking> knownHistory =
          allHistory.stream()
              .filter(b -> !b.getCreatedAt().isAfter(cutoff))
              .filter(b -> b.getStartTime().isBefore(cutoff) && b.getEndTime().isAfter(hFrom))
              .toList();
      List<UsageSession> knownUsage =
          allUsage.stream()
              .filter(s -> s.getStartedAt().isBefore(cutoff))
              .toList();
      var ctx =
          new PredictionContext(
              item, List.of(slot), knownFuture, knownHistory, knownUsage, cutoff, lookbackDays);
      var forecast = predictor.predict(ctx).get(0);
      boolean actualBlocked =
          allHistory.stream()
              .anyMatch(
                  b -> b.getStartTime().isBefore(slot.end()) && b.getEndTime().isAfter(slot.start()));
      boolean predictedBlocked = forecast.status() != PredictedStatus.AVAILABLE;
      if (predictedBlocked == actualBlocked) {
        correct++;
      }
      if (predictedBlocked) {
        predictedUnavailable++;
      }
      if (actualBlocked) {
        actualUnavailable++;
      }
      if (predictedBlocked && actualBlocked) {
        trueUnavailable++;
      }
      double actual = actualBlocked ? 0.0 : 1.0;
      brierSum += Math.pow(forecast.probabilityAvailable() - actual, 2);
      n++;
    }
    registry.counter("prediction.evaluations").increment();
    return new EvaluationResponse(
        item.getId(), predictor.method(), from, to, n,
        correct, round3((double) correct / n),
        predictedUnavailable == 0 ? 0.0 : round3((double) trueUnavailable / predictedUnavailable),
        actualUnavailable == 0 ? 0.0 : round3((double) trueUnavailable / actualUnavailable),
        round3(brierSum / n),
        "Backtest on recorded data with as-of cutoffs; consistency check, not a future-accuracy claim.");
  }

  private AvailabilityPredictor predictor(String method) {
    String key = method == null || method.isBlank() ? defaultMethod : method;
    AvailabilityPredictor predictor = predictors.get(key);
    if (predictor == null) {
      throw new IllegalArgumentException(
          "Unknown prediction method '" + key + "'. Available: " + predictors.keySet());
    }
    return predictor;
  }

  private List<Slot> window(Instant from, Instant to, int slotMinutes) {
    if (from == null || to == null || !to.isAfter(from)) {
      throw new IllegalArgumentException("Prediction window requires 'from' before 'to'");
    }
    if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
      throw new IllegalArgumentException("Prediction window must not exceed 93 days");
    }
    if (slotMinutes < MIN_SLOT_MINUTES || slotMinutes > MAX_SLOT_MINUTES) {
      throw new IllegalArgumentException(
          "Slot must be between " + MIN_SLOT_MINUTES + " and " + MAX_SLOT_MINUTES + " minutes");
    }
    List<Slot> slots = new ArrayList<>();
    Instant cursor = from;
    while (cursor.isBefore(to)) {
      Instant end = cursor.plusSeconds((long) slotMinutes * 60);
      if (end.isAfter(to)) {
        end = to;
      }
      slots.add(new Slot(cursor, end));
      cursor = end;
      if (slots.size() > MAX_SLOTS) {
        throw new IllegalArgumentException("Too many slots (max " + MAX_SLOTS + "): widen slotMinutes");
      }
    }
    return slots;
  }

  private Equipment loadEquipment(Long equipmentId) {
    return equipment
        .findById(equipmentId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Equipment with id " + equipmentId + " not found"));
  }

  private static double round3(double v) {
    return Math.round(v * 1000.0) / 1000.0;
  }
}
