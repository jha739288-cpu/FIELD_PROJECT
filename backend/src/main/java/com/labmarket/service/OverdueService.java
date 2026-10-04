package com.labmarket.service;

import com.labmarket.entity.Alert;
import com.labmarket.entity.AlertStatus;
import com.labmarket.entity.AlertType;
import com.labmarket.entity.Booking;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.repository.AlertRepository;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Overdue detection. A live booking (CONFIRMED or CHECKED_IN) past its end flips
 * to OVERDUE once, the equipment follows (unless staff/sensor owns its state),
 * exactly one OPEN alert exists per booking, and exactly one audit row is written —
 * reruns are no-ops, so duplicate alerts cannot pile up.
 */
@Service
public class OverdueService {

  private static final Logger log = LoggerFactory.getLogger(OverdueService.class);

  /** Live states watched for overdue transition. */
  static final Set<BookingStatus> WATCHED =
      EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

  private final BookingRepository bookings;
  private final EquipmentRepository equipment;
  private final AlertRepository alerts;
  private final AuditService audit;
  private final Clock clock;
  private final Counter detectedCounter;

  public OverdueService(
      BookingRepository bookings,
      EquipmentRepository equipment,
      AlertRepository alerts,
      AuditService audit,
      Clock clock,
      MeterRegistry registry) {
    this.bookings = bookings;
    this.equipment = equipment;
    this.alerts = alerts;
    this.audit = audit;
    this.clock = clock;
    this.detectedCounter = registry.counter("overdue.detected");
  }

  /**
   * Sweeps watched bookings past their end. Returns the newly-overdue count.
   * Time comes from the injected {@link Clock} (fixed in tests).
   */
  @Transactional
  public int detectOverdue() {
    Instant now = clock.instant();
    List<Booking> candidates = bookings.findByStatusInAndEndTimeBefore(WATCHED, now);
    int detected = 0;
    for (Booking b : candidates) {
      Equipment item =
          equipment.findWithLockById(b.getEquipment().getId()).orElseGet(b::getEquipment);
      b.setStatus(BookingStatus.OVERDUE);
      if (item.getCurrentStatus() == EquipmentStatus.IN_USE
          || item.getCurrentStatus() == EquipmentStatus.RESERVED
          || item.getCurrentStatus() == EquipmentStatus.AVAILABLE) {
        item.setCurrentStatus(EquipmentStatus.OVERDUE);
      }
      if (!alerts.existsByBookingIdAndTypeAndStatus(b.getId(), AlertType.OVERDUE, AlertStatus.OPEN)) {
        Alert alert = new Alert();
        alert.setType(AlertType.OVERDUE);
        alert.setBooking(b);
        alert.setEquipment(item);
        alert.setStatus(AlertStatus.OPEN);
        alert.setMessage(
            "Booking " + b.getId() + " for equipment '" + item.getEquipmentCode()
                + "' is overdue since " + b.getEndTime());
        alerts.save(alert);
      }
      audit.log(
          AuditService.OVERDUE_DETECTED,
          "system",
          "BOOKING",
          b.getId(),
          "Booking past end time " + b.getEndTime());
      detected++;
      detectedCounter.increment();
      log.info("Booking {} marked OVERDUE", b.getId());
    }
    return detected;
  }
}
