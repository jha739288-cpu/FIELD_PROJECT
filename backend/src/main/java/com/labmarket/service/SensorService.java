package com.labmarket.service;

import com.labmarket.dto.SensorEventRequest;
import com.labmarket.dto.SensorEventResponse;
import com.labmarket.dto.SensorKeyResponse;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.SensorEvent;
import com.labmarket.entity.SensorStatus;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.exception.SensorUnauthorizedException;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.SensorEventRepository;
import com.labmarket.security.SensorRateLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sensor ingest. Devices are untrusted and authenticate per equipment item with
 * an {@code X-Sensor-Key} header (SHA-256 compared in constant time).
 *
 * <p>Status mapping (MAINTENANCE is staff-owned and never auto-changed):
 * <ul>
 *   <li>IN_USE → IN_USE, but only from AVAILABLE/RESERVED/SENSOR_OFFLINE and only when
 *       the current draw supports it (≥ {@value #IN_USE_AMPS} A, or status-only report).</li>
 *   <li>IDLE → AVAILABLE, but only from IN_USE/SENSOR_OFFLINE and only with no live
 *       CHECKED_IN booking (an open session owns the status).</li>
 *   <li>OFFLINE → SENSOR_OFFLINE.</li>
 *   <li>FAULT → MAINTENANCE (reversible by staff; maintenance state itself untouched).</li>
 * </ul>
 */
@Service
public class SensorService {

  private static final Logger log = LoggerFactory.getLogger(SensorService.class);

  /** Reports older than this are stored but flagged stale. */
  static final Duration STALE_AFTER = Duration.ofMinutes(5);

  /** Reports older than this are rejected outright (likely a stuck device clock). */
  static final Duration MAX_AGE = Duration.ofHours(24);

  /** Clock-skew allowance for device timestamps in the future. */
  static final Duration FUTURE_SKEW = Duration.ofSeconds(60);

  /** Minimum current draw (A) that corroborates an IN_USE report. */
  static final BigDecimal IN_USE_AMPS = new BigDecimal("0.10");

  private static final SecureRandom RANDOM = new SecureRandom();

  private final EquipmentRepository equipment;
  private final SensorEventRepository events;
  private final BookingRepository bookings;
  private final AuditService audit;
  private final SensorRateLimiter rateLimiter;
  private final MeterRegistry registry;
  private final Counter failureCounter;

  public SensorService(
      EquipmentRepository equipment,
      SensorEventRepository events,
      BookingRepository bookings,
      AuditService audit,
      SensorRateLimiter rateLimiter,
      MeterRegistry registry) {
    this.equipment = equipment;
    this.events = events;
    this.bookings = bookings;
    this.audit = audit;
    this.rateLimiter = rateLimiter;
    this.registry = registry;
    this.failureCounter = registry.counter("sensor.failures");
  }

  /** Provisions (or rotates) the device key for one item. Staff/admin only (see controller). */
  @Transactional
  public SensorKeyResponse provisionKey(String staffUsername, Long equipmentId) {
    Equipment item =
        equipment
            .findById(equipmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Equipment with id " + equipmentId + " not found"));
    String raw = randomKey();
    item.setSensorKeyHash(sha256Hex(raw));
    audit.log(
        AuditService.SENSOR_KEY_PROVISIONED,
        staffUsername,
        "EQUIPMENT",
        equipmentId,
        "Sensor key provisioned for '" + item.getEquipmentCode() + "'");
    log.info("Sensor key provisioned for equipment '{}' by '{}'", item.getEquipmentCode(), staffUsername);
    return new SensorKeyResponse(item.getId(), item.getEquipmentCode(), raw);
  }

  /** Validates, stores and applies one device report. */
  @Transactional
  public SensorEventResponse ingest(String rawKey, SensorEventRequest req) {
    String code = req.equipmentCode().trim();
    // Rate-limit BEFORE any DB work, on both the device namespace and globally,
    // so key-probing and event floods are cheap to absorb.
    rateLimiter.check("sensor:" + code);
    rateLimiter.check("sensor:global");
    Equipment item = equipment.findByEquipmentCode(code).orElse(null);
    if (item == null) {
      // Deliberately 401, not 404: callers without a valid key must not learn
      // which equipment codes exist (enumeration oracle).
      audit.log(
          AuditService.SENSOR_EVENT, "sensor:unknown", "EQUIPMENT", null,
          "Rejected event for unknown equipment code");
      throw new SensorUnauthorizedException("Invalid sensor key or unknown equipment");
    }
    authenticate(item, rawKey);
    // Serialize with booking/QR transitions on the same row.
    item = equipment.findWithLockById(item.getId()).orElseThrow();

    Instant now = Instant.now();
    Instant occurred = req.timestamp().truncatedTo(ChronoUnit.MILLIS);
    if (occurred.isAfter(now.plus(FUTURE_SKEW))) {
      failureCounter.increment();
      throw new IllegalArgumentException("Event timestamp is in the future");
    }
    if (occurred.isBefore(now.minus(MAX_AGE))) {
      failureCounter.increment();
      throw new IllegalArgumentException("Event timestamp is too old");
    }
    boolean stale = occurred.isBefore(now.minus(STALE_AFTER));

    SensorEvent event = new SensorEvent();
    event.setEquipment(item);
    event.setStatus(req.status());
    event.setCurrentValue(req.currentValue());
    event.setOccurredAt(occurred);
    event.setReceivedAt(now);
    event.setStale(stale);
    events.save(event);
    registry.counter("sensor.events", "stale", String.valueOf(stale)).increment();

    EquipmentStatus applied = applyStatus(item, req.status(), req.currentValue());
    if (stale) {
      log.warn("Stale sensor event {} from '{}' (occurred {})", event.getId(), code, occurred);
    }
    if (req.status() == SensorStatus.IN_USE && req.currentValue() != null
        && req.currentValue().compareTo(IN_USE_AMPS) < 0) {
      log.warn(
          "Contradictory sensor event {} from '{}': IN_USE reported while drawing {} A",
          event.getId(), code, req.currentValue());
    }
    String detail =
        "status=" + req.status() + " current=" + req.currentValue() + "A stale=" + stale
            + " applied=" + item.getCurrentStatus();
    audit.log(AuditService.SENSOR_EVENT, "sensor:" + code, "EQUIPMENT", item.getId(), detail);
    log.info("Sensor event {} from '{}': {}", event.getId(), code, detail);
    return new SensorEventResponse(
        event.getId(), code, req.status(), req.currentValue(), occurred, now, stale,
        item.getCurrentStatus());
  }

  @Transactional(readOnly = true)
  public Page<SensorEventResponse> history(String equipmentCode, Pageable pageable) {
    Equipment item =
        equipment
            .findByEquipmentCode(equipmentCode.trim())
            .orElseThrow(
                () -> new ResourceNotFoundException("Unknown equipment code '" + equipmentCode + "'"));
    return events
        .findByEquipmentEquipmentCode(item.getEquipmentCode(), pageable)
        .map(
            e ->
                new SensorEventResponse(
                    e.getId(), item.getEquipmentCode(), e.getStatus(), e.getCurrentValue(),
                    e.getOccurredAt(), e.getReceivedAt(), e.isStale(), null));
  }

  private void authenticate(Equipment item, String rawKey) {
    if (rawKey == null || rawKey.isBlank() || item.getSensorKeyHash() == null
        || !constantTimeEquals(sha256Hex(rawKey.trim()), item.getSensorKeyHash())) {
      failureCounter.increment();
      log.debug("Sensor authentication failed");
      throw new SensorUnauthorizedException("Invalid sensor key");
    }
  }

  /**
   * Applies the mapping; returns the applied status, or null when the equipment
   * was deliberately left untouched (documented in the detail string instead).
   */
  private EquipmentStatus applyStatus(Equipment item, SensorStatus reported, BigDecimal amps) {
    if (item.getCurrentStatus() == EquipmentStatus.MAINTENANCE) {
      return null; // staff-owned state: sensors never clear it
    }
    return switch (reported) {
      case IN_USE -> {
        boolean corroborated = amps == null || amps.compareTo(IN_USE_AMPS) >= 0;
        if (!corroborated) {
          yield null; // claims IN_USE while drawing ~nothing: contradictory, ignore
        }
        if (item.getCurrentStatus() == EquipmentStatus.AVAILABLE
            || item.getCurrentStatus() == EquipmentStatus.RESERVED
            || item.getCurrentStatus() == EquipmentStatus.SENSOR_OFFLINE) {
          item.setCurrentStatus(EquipmentStatus.IN_USE);
          yield EquipmentStatus.IN_USE;
        }
        yield null;
      }
      case IDLE -> {
        if ((item.getCurrentStatus() == EquipmentStatus.IN_USE
                || item.getCurrentStatus() == EquipmentStatus.SENSOR_OFFLINE)
            && !bookings.existsByEquipmentIdAndStatus(item.getId(), BookingStatus.CHECKED_IN)) {
          item.setCurrentStatus(EquipmentStatus.AVAILABLE);
          yield EquipmentStatus.AVAILABLE;
        }
        yield null;
      }
      case OFFLINE -> {
        item.setCurrentStatus(EquipmentStatus.SENSOR_OFFLINE);
        yield EquipmentStatus.SENSOR_OFFLINE;
      }
      case FAULT -> {
        item.setCurrentStatus(EquipmentStatus.MAINTENANCE);
        yield EquipmentStatus.MAINTENANCE;
      }
    };
  }

  private static boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  private static String randomKey() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String sha256Hex(String raw) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
