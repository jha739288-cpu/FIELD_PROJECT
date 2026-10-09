package com.labmarket.service;

import com.labmarket.dto.AdminOverviewResponse;
import com.labmarket.dto.BookingAnalyticsResponse;
import com.labmarket.dto.EquipmentDashboardResponse;
import com.labmarket.dto.EquipmentDashboardResponse.BookingBriefResponse;
import com.labmarket.dto.MySummaryResponse;
import com.labmarket.dto.SensorAnalyticsResponse;
import com.labmarket.dto.SummaryResponse;
import com.labmarket.dto.UsageTrendResponse;
import com.labmarket.dto.UsageTrendResponse.TrendPoint;
import com.labmarket.dto.UtilizationResponse;
import com.labmarket.dto.UtilizationResponse.ItemUtilization;
import com.labmarket.dto.VendorDashboardResponse;
import com.labmarket.dto.VendorDashboardResponse.BookingBrief;
import com.labmarket.entity.AlertStatus;
import com.labmarket.entity.AlertType;
import com.labmarket.entity.Booking;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.Role;
import com.labmarket.entity.SensorEvent;
import com.labmarket.entity.SensorStatus;
import com.labmarket.entity.UsageSession;
import com.labmarket.entity.UsageStatus;
import com.labmarket.entity.User;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.repository.AlertRepository;
import com.labmarket.repository.AuditEventRepository;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.SensorEventRepository;
import com.labmarket.repository.UsageSessionRepository;
import com.labmarket.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Analytics computed from live rows — no hard-coded numbers anywhere.
 *
 * <ul>
 *   <li><b>Current status</b>: equipment/booking counts reflect this instant.</li>
 *   <li><b>Historical usage</b>: session and audit rows inside explicit windows.</li>
 *   <li><b>Calculated utilization</b>: usage seconds clipped (and day-split) to the
 *       window, divided by fleet capacity over the same window.</li>
 * </ul>
 */
@Service
public class DashboardService {

  /** Trailing window used by the summary endpoint. */
  static final Duration SUMMARY_WINDOW = Duration.ofDays(30);

  /** Longest window the computation endpoints accept. */
  private static final Duration MAX_WINDOW = Duration.ofDays(93);

  private final EquipmentRepository equipment;
  private final BookingRepository bookings;
  private final UsageSessionRepository sessions;
  private final SensorEventRepository sensorEvents;
  private final AlertRepository alerts;
  private final AuditEventRepository audits;
  private final UserRepository users;
  private final Clock clock;

  public DashboardService(
      EquipmentRepository equipment,
      BookingRepository bookings,
      UsageSessionRepository sessions,
      SensorEventRepository sensorEvents,
      AlertRepository alerts,
      AuditEventRepository audits,
      UserRepository users,
      Clock clock) {
    this.equipment = equipment;
    this.bookings = bookings;
    this.sessions = sessions;
    this.sensorEvents = sensorEvents;
    this.alerts = alerts;
    this.audits = audits;
    this.users = users;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public SummaryResponse summary() {
    Instant to = clock.instant();
    Instant from = to.minus(SUMMARY_WINDOW);
    long operational = equipment.countByMaintenanceStatus(MaintenanceStatus.OPERATIONAL);
    long totalUsage = mergedUsage(sessions.findOverlapping(from, to), from, to, to);
    long idle = sensorEvents.countByStatusAndCreatedAtBetween(SensorStatus.IDLE, from, to);
    long inUse = sensorEvents.countByStatusAndCreatedAtBetween(SensorStatus.IN_USE, from, to);
    long offline = sensorEvents.countByStatusAndCreatedAtBetween(SensorStatus.OFFLINE, from, to);
    long faults = sensorEvents.countByStatusAndCreatedAtBetween(SensorStatus.FAULT, from, to);
    long events = sensorEvents.countByCreatedAtBetween(from, to);
    long stale = sensorEvents.countByStaleTrueAndCreatedAtBetween(from, to);
    return new SummaryResponse(
        new SummaryResponse.EquipmentSummary(
            equipment.count(),
            equipment.countByCurrentStatus(EquipmentStatus.AVAILABLE),
            equipment.countByCurrentStatus(EquipmentStatus.RESERVED),
            equipment.countByCurrentStatus(EquipmentStatus.IN_USE),
            equipment.countByCurrentStatus(EquipmentStatus.OVERDUE),
            equipment.countByCurrentStatus(EquipmentStatus.MAINTENANCE),
            equipment.countByCurrentStatus(EquipmentStatus.SENSOR_OFFLINE)),
        bookingSummary(),
        new SummaryResponse.UsageSummary(
            sessions.countByStartedAtBetween(from, to),
            sessions.countByStatusAndEndedAtBetween(UsageStatus.COMPLETED, from, to),
            totalUsage,
            round2(totalUsage / 3600.0)),
        new SummaryResponse.SensorSummary(
            events, stale, faults, idle, inUse, offline, reliability(events, stale)),
        new SummaryResponse.AlertSummary(
            alerts.countByTypeAndStatus(AlertType.OVERDUE, AlertStatus.OPEN),
            alerts.countByTypeAndStatus(AlertType.OVERDUE, AlertStatus.RESOLVED)),
        percent(totalUsage, operational > 0 ? Duration.between(from, to).getSeconds() * operational : 0),
        audits.countByEventTypeAndCreatedAtBetween(AuditService.BOOKING_CONFLICT, from, to),
        from,
        to,
        to);
  }

  @Transactional(readOnly = true)
  public UtilizationResponse utilization(Instant from, Instant to, Long equipmentId) {
    requireValidWindow(from, to);
    Instant now = clock.instant();
    List<Equipment> items =
        equipmentId == null
            ? equipment.findAll()
            : List.of(
                equipment
                    .findById(equipmentId)
                    .orElseThrow(
                        () ->
                            new ResourceNotFoundException(
                                "Equipment with id " + equipmentId + " not found")));
    List<UsageSession> found =
        equipmentId == null
            ? sessions.findOverlapping(from, to)
            : sessions.findOverlappingForEquipment(equipmentId, from, to);
    Map<Long, List<Interval>> merged = mergedByEquipment(found, from, to, now);
    long windowSeconds = Math.max(1, Duration.between(from, to).getSeconds());
    List<ItemUtilization> perItem = new ArrayList<>();
    long totalUsage = 0;
    int operational = 0;
    for (Equipment item : items) {
      boolean isOperational = item.getMaintenanceStatus() == MaintenanceStatus.OPERATIONAL;
      long used = sum(merged.getOrDefault(item.getId(), List.of()));
      if (isOperational) {
        operational++;
        totalUsage += used;
      }
      perItem.add(
          new ItemUtilization(
              item.getId(), item.getEquipmentCode(), item.getName(), used,
              percent(used, windowSeconds), isOperational, item.getMaintenanceStatus().name()));
    }
    perItem.sort((a, b) -> Long.compare(b.usageSeconds(), a.usageSeconds()));
    long capacity = operational > 0 ? windowSeconds * operational : 0;
    return new UtilizationResponse(
        from, to, items.size(), operational, windowSeconds, totalUsage,
        percent(totalUsage, capacity), perItem);
  }

  @Transactional(readOnly = true)
  public EquipmentDashboardResponse equipmentDashboard(Long equipmentId, Instant from, Instant to) {
    Instant[] window = defaultWindow(from, to);
    Instant wFrom = window[0];
    Instant wTo = window[1];
    Equipment item =
        equipment
            .findById(equipmentId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Equipment with id " + equipmentId + " not found"));
    Map<BookingStatus, Long> byStatus = new EnumMap<>(BookingStatus.class);
    for (BookingStatus s : BookingStatus.values()) {
      byStatus.put(s, bookings.countByEquipmentIdAndStatus(equipmentId, s));
    }
    List<UsageSession> overlapping = sessions.findOverlappingForEquipment(equipmentId, wFrom, wTo);
    long used = usageWithin(overlapping, wFrom, wTo, clock.instant());
    long completed =
        overlapping.stream().filter(s -> s.getStatus() == UsageStatus.COMPLETED).count();
    long windowSeconds = Math.max(1, Duration.between(wFrom, wTo).getSeconds());
    List<BookingBriefResponse> recent =
        bookings
            .search(equipmentId, null, PageRequest.of(0, 5,
                org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC, "createdAt")))
            .getContent().stream()
            .map(b -> new BookingBriefResponse(b.getId(), b.getStartTime(), b.getEndTime(), b.getStatus()))
            .toList();
    return new EquipmentDashboardResponse(
        item.getId(), item.getEquipmentCode(), item.getName(), item.getCategory(),
        item.getCondition(), item.getCurrentStatus(), item.getMaintenanceStatus(),
        wFrom, wTo, byStatus, overlapping.size(), completed, used,
        percent(used, windowSeconds), recent);
  }

  @Transactional(readOnly = true)
  public UsageTrendResponse usageTrend(Instant from, Instant to) {
    Instant[] window = defaultWindow(from, to);
    Instant wFrom = window[0];
    Instant wTo = window[1];
    Instant now = clock.instant();
    Map<LocalDate, TrendAccumulator> days = new LinkedHashMap<>();
    for (LocalDate d = dayOf(wFrom); !d.isAfter(dayOf(wTo.minusSeconds(1))); d = d.plusDays(1)) {
      days.put(d, new TrendAccumulator());
    }
    List<UsageSession> overlapping = sessions.findOverlapping(wFrom, wTo);
    // Merged per item first: overlapping sessions of one item share time only once.
    for (List<Interval> merged : mergedByEquipment(overlapping, wFrom, wTo, now).values()) {
      for (Interval interval : merged) {
        splitInterval(interval, days);
      }
    }
    for (UsageSession s : overlapping) {
      Instant end = effectiveEnd(s, now);
      if (!end.isBefore(wFrom) && end.isBefore(wTo) && s.getEndedAt() != null) {
        days.get(dayOf(end)).sessionsEnded++;
      }
    }
    for (Booking b : bookings.findByCreatedAtBetween(wFrom, wTo)) {
      TrendAccumulator acc = days.get(dayOf(b.getCreatedAt()));
      if (acc != null) {
        acc.bookingsCreated++;
      }
    }
    List<TrendPoint> points = new ArrayList<>();
    for (Map.Entry<LocalDate, TrendAccumulator> e : days.entrySet()) {
      points.add(
          new TrendPoint(
              e.getKey(), e.getValue().usageSeconds, round2(e.getValue().usageSeconds / 3600.0),
              e.getValue().sessionsEnded, e.getValue().bookingsCreated));
    }
    return new UsageTrendResponse(wFrom, wTo, "DAY", points);
  }

  /** Personal analytics for one student: own bookings and own usage only. */
  @Transactional(readOnly = true)
  public MySummaryResponse mySummary(String username) {
    User viewer =
        users
            .findByUsername(username)
            .orElseThrow(() -> new AccessDeniedException("Access denied"));
    Instant to = clock.instant();
    Instant from = to.minus(SUMMARY_WINDOW);
    long total = 0;
    Map<BookingStatus, Long> counts = new EnumMap<>(BookingStatus.class);
    for (BookingStatus s : BookingStatus.values()) {
      long c = bookings.countByOwnerUsernameAndStatus(username, s);
      counts.put(s, c);
      total += c;
    }
    List<UsageSession> own = sessions.findOverlappingForUser(viewer.getId(), from, to);
    long started =
        own.stream().filter(s -> !s.getStartedAt().isBefore(from) && s.getStartedAt().isBefore(to)).count();
    long completed = own.stream().filter(s -> s.getStatus() == UsageStatus.COMPLETED).count();
    long totalUsage = mergedUsage(own, from, to, to);
    return new MySummaryResponse(
        username,
        new SummaryResponse.BookingSummary(
            total, counts.get(BookingStatus.PENDING), counts.get(BookingStatus.CONFIRMED),
            counts.get(BookingStatus.CHECKED_IN), counts.get(BookingStatus.COMPLETED),
            counts.get(BookingStatus.CANCELLED), counts.get(BookingStatus.OVERDUE),
            counts.get(BookingStatus.REJECTED)),
        new SummaryResponse.UsageSummary(started, completed, totalUsage, round2(totalUsage / 3600.0)),
        from,
        to,
        to);
  }

  /** Platform overview for admins: real counts plus the five most recent rows. */
  @Transactional(readOnly = true)
  public AdminOverviewResponse adminOverview() {
    Map<String, Long> usersByRole = new LinkedHashMap<>();
    usersByRole.put("ADMIN", users.countByRoleName("ADMIN"));
    usersByRole.put("USER", users.countByRoleName("USER"));
    usersByRole.put("VENDOR", users.countByRoleName("VENDOR"));
    Map<String, Long> equipmentByStatus = new LinkedHashMap<>();
    for (EquipmentStatus s : EquipmentStatus.values()) {
      equipmentByStatus.put(s.name(), equipment.countByCurrentStatus(s));
    }
    Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
    for (BookingStatus s : BookingStatus.values()) {
      bookingsByStatus.put(s.name(), bookings.countByStatus(s));
    }
    PageRequest recent = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
    List<AdminOverviewResponse.UserBrief> recentUsers =
        users.findAll(recent).getContent().stream()
            .map(
                u ->
                    new AdminOverviewResponse.UserBrief(
                        u.getId(), u.getUsername(), u.getEmail(),
                        u.getRoles().stream().map(Role::getName).sorted().toList(),
                        u.getCreatedAt()))
            .toList();
    List<AdminOverviewResponse.BookingBrief> recentBookings =
        bookings.search(null, null, recent).getContent().stream()
            .map(
                b ->
                    new AdminOverviewResponse.BookingBrief(
                        b.getId(), b.getEquipment().getEquipmentCode(),
                        b.getOwner().getUsername(), b.getStatus().name(), b.getStartTime()))
            .toList();
    List<AdminOverviewResponse.EquipmentBrief> recentEquipment =
        equipment.findAll(recent).getContent().stream()
            .map(
                e ->
                    new AdminOverviewResponse.EquipmentBrief(
                        e.getId(), e.getEquipmentCode(), e.getName(),
                        e.getCurrentStatus().name()))
            .toList();
    return new AdminOverviewResponse(
        users.count(),
        usersByRole,
        equipmentByStatus,
        bookingsByStatus,
        alerts.countByTypeAndStatus(AlertType.OVERDUE, AlertStatus.OPEN),
        recentUsers,
        recentBookings,
        recentEquipment);
  }

  /** A vendor's own marketplace numbers (inventory, demand, utilization). */
  @Transactional(readOnly = true)
  public VendorDashboardResponse vendorSummary(String username) {
    User viewer =
        users
            .findByUsername(username)
            .orElseThrow(() -> new AccessDeniedException("Access denied"));
    Long ownerId = viewer.getId();
    List<Equipment> items = equipment.findByCreatedById(ownerId, Pageable.unpaged()).getContent();
    Map<String, Long> equipmentByStatus = new LinkedHashMap<>();
    for (Equipment item : items) {
      equipmentByStatus.merge(item.getCurrentStatus().name(), 1L, Long::sum);
    }
    Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
    long totalBookings = 0;
    for (Object[] row : bookings.countByStatusForOwner(ownerId)) {
      BookingStatus status = (BookingStatus) row[0];
      long count = (Long) row[1];
      bookingsByStatus.put(status.name(), count);
      totalBookings += count;
    }
    long completedSessions = sessions.countCompletedForOwner(ownerId);
    long usageSeconds = sessions.totalCompletedSecondsForOwner(ownerId);
    List<VendorDashboardResponse.BookingBrief> recent =
        bookings.findByEquipmentOwnerId(ownerId, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
            .getContent().stream()
            .map(
                b ->
                    new VendorDashboardResponse.BookingBrief(
                        b.getId(), b.getEquipment().getEquipmentCode(),
                        b.getOwner().getUsername(), b.getStatus().name(),
                        b.getStartTime().toString()))
            .toList();
    return new VendorDashboardResponse(
        username,
        items.size(),
        equipmentByStatus,
        totalBookings,
        bookingsByStatus,
        completedSessions,
        usageSeconds,
        Math.round(usageSeconds / 36.0) / 100.0,
        recent);
  }

  /** Booking analytics over bookings created in the window (UTC day buckets). */
  @Transactional(readOnly = true)
  public BookingAnalyticsResponse bookingAnalytics(Instant from, Instant to, Long equipmentId) {
    Instant[] window = defaultWindow(from, to);
    Instant wFrom = window[0];
    Instant wTo = window[1];
    List<BookingRepository.BookingRow> rows = bookings.findRowsInWindow(wFrom, wTo);
    if (equipmentId != null) {
      rows = rows.stream().filter(r -> equipmentId.equals(r.getEquipmentId())).toList();
    }
    Map<String, Long> byStatus = new LinkedHashMap<>();
    Map<String, Long> byDay = new LinkedHashMap<>();
    Map<Long, BookingAnalyticsResponse.EquipmentBookingCount> byEquipment = new LinkedHashMap<>();
    Map<Long, String> codes = new LinkedHashMap<>();
    for (var row : rows) {
      byStatus.merge(row.getStatus().name(), 1L, Long::sum);
      byDay.merge(dayOf(row.getCreated()).toString(), 1L, Long::sum);
      codes.putIfAbsent(row.getEquipmentId(), row.getCode());
      byEquipment.merge(
          row.getEquipmentId(),
          new BookingAnalyticsResponse.EquipmentBookingCount(row.getEquipmentId(), row.getCode(), 1L),
          (a, b) -> new BookingAnalyticsResponse.EquipmentBookingCount(a.equipmentId(), a.equipmentCode(), a.count() + b.count()));
    }
    List<BookingAnalyticsResponse.EquipmentBookingCount> equipmentCounts =
        byEquipment.values().stream()
            .sorted((a, b) -> Long.compare(b.count(), a.count()))
            .toList();
    return new BookingAnalyticsResponse(
        wFrom, wTo, rows.size(), byStatus,
        byDay.entrySet().stream()
            .map(e -> new BookingAnalyticsResponse.DayCount(e.getKey(), e.getValue()))
            .toList(),
        equipmentCounts,
        byStatus.getOrDefault(BookingStatus.CANCELLED.name(), 0L),
        byStatus.getOrDefault(BookingStatus.OVERDUE.name(), 0L));
  }

  /** Sensor analytics over events that occurred in the window. */
  @Transactional(readOnly = true)
  public SensorAnalyticsResponse sensorAnalytics(Instant from, Instant to, Long equipmentId) {
    Instant[] window = defaultWindow(from, to);
    Instant wFrom = window[0];
    Instant wTo = window[1];
    Map<String, Long> byStatus = new LinkedHashMap<>();
    Map<String, Long> byEquipment = new LinkedHashMap<>();
    long total;
    long stale;
    if (equipmentId == null) {
      total = 0;
      for (var row : sensorEvents.countByStatusInWindow(wFrom, wTo)) {
        byStatus.put(row.getStatus().name(), row.getTotal());
        total += row.getTotal();
      }
      for (var row : sensorEvents.countByEquipmentInWindow(wFrom, wTo)) {
        byEquipment.put(row.getCode(), row.getTotal());
      }
      stale =
          sensorEvents.countByStaleTrueAndOccurredAtBetween(wFrom, wTo);
    } else {
      List<SensorEvent> rows = sensorEvents.findByEquipmentInWindow(equipmentId, wFrom, wTo);
      for (SensorEvent e : rows) {
        byStatus.merge(e.getStatus().name(), 1L, Long::sum);
        byEquipment.merge(e.getEquipment().getEquipmentCode(), 1L, Long::sum);
      }
      total = rows.size();
      stale = rows.stream().filter(SensorEvent::isStale).count();
    }
    long faults = byStatus.getOrDefault(SensorStatus.FAULT.name(), 0L);
    long offline = byStatus.getOrDefault(SensorStatus.OFFLINE.name(), 0L);
    return new SensorAnalyticsResponse(
        wFrom, wTo, total, byStatus, stale, reliability(total, stale), faults, offline,
        byEquipment.entrySet().stream()
            .map(e -> new SensorAnalyticsResponse.EquipmentSensorCount(e.getKey(), e.getValue()))
            .sorted((a, b) -> Long.compare(b.count(), a.count()))
            .toList());
  }

  /** Rejected overlap attempts recorded by booking conflict detection. */
  @Transactional(readOnly = true)
  public long conflictAttempts(Instant from, Instant to) {
    Instant[] window = defaultWindow(from, to);
    return audits.countByEventTypeAndCreatedAtBetween(
        AuditService.BOOKING_CONFLICT, window[0], window[1]);
  }

  /** Freshness share: on-time stored events ÷ all stored events; null when there are none. */
  static Double reliability(long total, long stale) {
    if (total <= 0) {
      return null;
    }
    return round2((double) (total - stale) / total);
  }

  private SummaryResponse.BookingSummary bookingSummary() {
    long total = 0;
    Map<BookingStatus, Long> counts = new EnumMap<>(BookingStatus.class);
    for (BookingStatus s : BookingStatus.values()) {
      long c = bookings.countByStatus(s);
      counts.put(s, c);
      total += c;
    }
    return new SummaryResponse.BookingSummary(
        total, counts.get(BookingStatus.PENDING), counts.get(BookingStatus.CONFIRMED),
        counts.get(BookingStatus.CHECKED_IN), counts.get(BookingStatus.COMPLETED),
        counts.get(BookingStatus.CANCELLED), counts.get(BookingStatus.OVERDUE),
        counts.get(BookingStatus.REJECTED));
  }

  private Instant[] defaultWindow(Instant from, Instant to) {
    Instant now = clock.instant();
    Instant wTo = to == null ? now : to;
    Instant wFrom = from == null ? wTo.minus(SUMMARY_WINDOW) : from;
    requireValidWindow(wFrom, wTo);
    return new Instant[] {wFrom, wTo};
  }

  private void requireValidWindow(Instant from, Instant to) {
    if (from == null || to == null || !to.isAfter(from)) {
      throw new IllegalArgumentException("Query window requires 'from' before 'to'");
    }
    if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
      throw new IllegalArgumentException("Query window must not exceed 93 days");
    }
  }

  /**
   * Seconds of the given sessions inside {@code [from, to)}.
   * Overlapping sessions of the SAME equipment are merged first so shared time
   * is never double-counted; sessions of different items always add up. Open
   * sessions clamp at {@code min(now, to)}.
   */
  static long usageWithin(List<UsageSession> found, Instant from, Instant to, Instant now) {
    long total = 0;
    for (List<Interval> merged : mergedByEquipment(found, from, to, now).values()) {
      total += sum(merged);
    }
    return total;
  }

  /** Total merged usage across all given sessions (see {@link #usageWithin}). */
  static long mergedUsage(List<UsageSession> found, Instant from, Instant to, Instant now) {
    return usageWithin(found, from, to, now);
  }

  /** Clipped intervals merged per equipment id. */
  static Map<Long, List<Interval>> mergedByEquipment(
      List<UsageSession> found, Instant from, Instant to, Instant now) {
    Map<Long, List<Interval>> perItem = new LinkedHashMap<>();
    for (UsageSession s : found) {
      Instant start = s.getStartedAt().isBefore(from) ? from : s.getStartedAt();
      Instant end = effectiveEnd(s, now);
      if (end.isAfter(to)) {
        end = to;
      }
      if (end.isAfter(start)) {
        perItem.computeIfAbsent(s.getEquipment().getId(), k -> new ArrayList<>())
            .add(new Interval(start, end));
      }
    }
    for (Map.Entry<Long, List<Interval>> e : perItem.entrySet()) {
      e.setValue(merge(e.getValue()));
    }
    return perItem;
  }

  private static List<Interval> merge(List<Interval> intervals) {
    List<Interval> sorted =
        intervals.stream().sorted((a, b) -> a.s().compareTo(b.s())).toList();
    List<Interval> merged = new ArrayList<>();
    for (Interval i : sorted) {
      if (!merged.isEmpty() && !i.s().isAfter(merged.get(merged.size() - 1).e())) {
        Interval last = merged.remove(merged.size() - 1);
        merged.add(new Interval(last.s(), max(last.e(), i.e())));
      } else {
        merged.add(i);
      }
    }
    return merged;
  }

  private static long sum(List<Interval> intervals) {
    long total = 0;
    for (Interval i : intervals) {
      total += Duration.between(i.s(), i.e()).getSeconds();
    }
    return total;
  }

  private record Interval(Instant s, Instant e) {}

  private static Instant max(Instant a, Instant b) {
    return a.isAfter(b) ? a : b;
  }

  private static Instant effectiveEnd(UsageSession s, Instant now) {
    if (s.getEndedAt() != null) {
      return s.getEndedAt();
    }
    return s.getStartedAt().isAfter(now) ? s.getStartedAt() : now;
  }

  private static void splitInterval(Interval interval, Map<LocalDate, TrendAccumulator> days) {
    Instant cursor = interval.s();
    Instant end = interval.e();
    while (cursor.isBefore(end)) {
      Instant dayEnd = dayOf(cursor).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
      Instant sliceEnd = end.isBefore(dayEnd) ? end : dayEnd;
      TrendAccumulator acc = days.get(dayOf(cursor));
      if (acc != null) {
        acc.usageSeconds += Duration.between(cursor, sliceEnd).getSeconds();
      }
      cursor = sliceEnd;
    }
  }

  private static LocalDate dayOf(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).toLocalDate();
  }

  private static double percent(long part, long whole) {
    if (whole <= 0) {
      return 0.0;
    }
    return round2(100.0 * part / whole);
  }

  private static double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private static class TrendAccumulator {
    long usageSeconds;
    long sessionsEnded;
    long bookingsCreated;
  }
}
