package com.labmarket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labmarket.entity.AlertStatus;
import com.labmarket.entity.AlertType;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.Role;
import com.labmarket.entity.SensorStatus;
import com.labmarket.entity.UsageSession;
import com.labmarket.entity.UsageStatus;
import com.labmarket.entity.User;
import com.labmarket.repository.AlertRepository;
import com.labmarket.repository.AuditEventRepository;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.SensorEventRepository;
import com.labmarket.repository.UsageSessionRepository;
import com.labmarket.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure unit tests for analytics math with a fixed clock (no Spring context).
 *
 * <p>Behavior changes vs. the first dashboard version (all documented in
 * {@code docs/evaluation/utilization.md}): usage intervals merge per item
 * (no double-counting), non-operational items leave the capacity denominator,
 * summary usage/alerts/sensors come from merged counts and type-scoped queries.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

  @Mock private EquipmentRepository equipment;
  @Mock private BookingRepository bookings;
  @Mock private UsageSessionRepository sessions;
  @Mock private SensorEventRepository sensorEvents;
  @Mock private AlertRepository alerts;
  @Mock private AuditEventRepository audits;
  @Mock private UserRepository users;

  private DashboardService service;

  @BeforeEach
  void setUp() {
    service =
        new DashboardService(
            equipment, bookings, sessions, sensorEvents, alerts, audits, users,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void summaryMapsLiveCounts() {
    when(equipment.count()).thenReturn(3L);
    when(equipment.countByCurrentStatus(any())).thenReturn(0L);
    when(equipment.countByCurrentStatus(EquipmentStatus.AVAILABLE)).thenReturn(1L);
    when(equipment.countByCurrentStatus(EquipmentStatus.IN_USE)).thenReturn(1L);
    when(equipment.countByCurrentStatus(EquipmentStatus.MAINTENANCE)).thenReturn(1L);
    when(equipment.countByMaintenanceStatus(MaintenanceStatus.OPERATIONAL)).thenReturn(3L);
    when(bookings.countByStatus(any())).thenReturn(0L);
    when(bookings.countByStatus(BookingStatus.CONFIRMED)).thenReturn(2L);
    when(bookings.countByStatus(BookingStatus.COMPLETED)).thenReturn(5L);
    when(sessions.countByStartedAtBetween(any(), any())).thenReturn(7L);
    when(sessions.countByStatusAndEndedAtBetween(any(), any(), any())).thenReturn(6L);
    Equipment item = equipment(9L, "OSC-001");
    // Merged usage replaces the old DB SUM: one 18000s session in the window.
    when(sessions.findOverlapping(any(), any()))
        .thenReturn(List.of(session(1L, item, NOW.minusSeconds(18_000), NOW)));
    when(sensorEvents.countByCreatedAtBetween(any(), any())).thenReturn(100L);
    when(sensorEvents.countByStaleTrueAndCreatedAtBetween(any(), any())).thenReturn(4L);
    when(sensorEvents.countByStatusAndCreatedAtBetween(any(), any(), any())).thenReturn(2L);
    when(alerts.countByTypeAndStatus(AlertType.OVERDUE, AlertStatus.OPEN)).thenReturn(1L);
    when(alerts.countByTypeAndStatus(AlertType.OVERDUE, AlertStatus.RESOLVED)).thenReturn(3L);
    when(audits.countByEventTypeAndCreatedAtBetween(any(), any(), any())).thenReturn(5L);

    var summary = service.summary();

    assertEquals(3L, summary.equipment().total());
    assertEquals(1L, summary.equipment().available());
    assertEquals(2L, summary.bookings().confirmed());
    assertEquals(5L, summary.bookings().completed());
    assertEquals(7L, summary.bookings().total());
    assertEquals(7L, summary.usage().sessionsStarted());
    assertEquals(18_000L, summary.usage().totalSeconds());
    assertEquals(5.0, summary.usage().totalHours());
    assertEquals(100L, summary.sensors().events());
    assertEquals(4L, summary.sensors().staleEvents());
    assertEquals(2L, summary.sensors().faults());
    assertEquals(2L, summary.sensors().idle());
    assertEquals(0.96, summary.sensors().reliability());
    assertEquals(1L, summary.alerts().open());
    assertEquals(5L, summary.conflictAttempts());
    // 18000s ÷ (30d × 3 items) ≈ 0.23%.
    assertEquals(0.23, summary.utilizationPercent(), 0.001);
  }

  @Test
  void emptyDatabaseYieldsZeros() {
    var summary = service.summary();

    assertEquals(0L, summary.equipment().total());
    assertEquals(0L, summary.bookings().total());
    assertEquals(0L, summary.usage().totalSeconds());
    assertEquals(0.0, summary.usage().totalHours());
    assertEquals(0.0, summary.utilizationPercent());
    assertEquals(0L, summary.sensors().events());
    assertNull(summary.sensors().reliability());
    assertEquals(0L, summary.conflictAttempts());

    var util = service.utilization(NOW.minusSeconds(3600), NOW, null);
    assertEquals(0, util.equipmentCount());
    assertEquals(0L, util.totalUsageSeconds());
    assertEquals(0.0, util.utilizationPercent());
  }

  @Test
  void utilizationClipsToWindow() {
    Instant from = NOW.minusSeconds(86_400);
    Equipment item = equipment(9L, "OSC-001");
    when(equipment.findAll()).thenReturn(List.of(item));
    // Session runs [from-1h, from+1h]: only 3600s fall inside the window.
    when(sessions.findOverlapping(from, NOW))
        .thenReturn(List.of(session(1L, item, from.minusSeconds(3600), from.plusSeconds(3600))));

    var res = service.utilization(from, NOW, null);

    assertEquals(3600L, res.totalUsageSeconds());
    assertEquals(86_400L, res.windowSeconds());
    assertEquals(4.17, res.utilizationPercent(), 0.001);
    assertEquals(1, res.items().size());
    assertEquals(3600L, res.items().get(0).usageSeconds());
  }

  @Test
  void utilizationClampsOpenSessionAtNow() {
    Instant from = NOW.minusSeconds(7200);
    Equipment item = equipment(9L, "OSC-001");
    when(equipment.findAll()).thenReturn(List.of(item));
    // ACTIVE session started 1h ago, still open: contributes exactly 3600s.
    UsageSession open = session(1L, item, NOW.minusSeconds(3600), null);
    open.setStatus(UsageStatus.ACTIVE);
    when(sessions.findOverlapping(from, NOW)).thenReturn(List.of(open));

    var res = service.utilization(from, NOW, null);

    assertEquals(3600L, res.totalUsageSeconds());
    assertEquals(50.0, res.utilizationPercent(), 0.001);
  }

  @Test
  void overlappingSessionsOfOneItemMergeInsteadOfDoubling() {
    Instant from = NOW.minusSeconds(86_400);
    Equipment item = equipment(9L, "OSC-001");
    when(equipment.findAll()).thenReturn(List.of(item));
    // [0h,2h] + [1h,3h] overlap by 1h: merged contribution is 3h, not 4h.
    Instant base = NOW.minusSeconds(10_800);
    when(sessions.findOverlapping(from, NOW))
        .thenReturn(
            List.of(
                session(1L, item, base, base.plusSeconds(7200)),
                session(2L, item, base.plusSeconds(3600), base.plusSeconds(10_800))));

    var res = service.utilization(from, NOW, null);

    assertEquals(10_800L, res.totalUsageSeconds());
  }

  @Test
  void nonOperationalItemsLeaveCapacityButStayListed() {
    Instant from = NOW.minusSeconds(86_400);
    Equipment good = equipment(9L, "OSC-001");
    Equipment broken = equipment(10L, "OSC-002");
    broken.setMaintenanceStatus(MaintenanceStatus.OUT_OF_SERVICE);
    when(equipment.findAll()).thenReturn(List.of(good, broken));
    when(sessions.findOverlapping(from, NOW))
        .thenReturn(
            List.of(
                session(1L, good, NOW.minusSeconds(3600), NOW),
                session(2L, broken, NOW.minusSeconds(3600), NOW)));

    var res = service.utilization(from, NOW, null);

    // Only the operational item counts toward capacity and totals.
    assertEquals(2, res.equipmentCount());
    assertEquals(1, res.operationalCount());
    assertEquals(3600L, res.totalUsageSeconds());
    assertEquals(4.17, res.utilizationPercent(), 0.001);
    var brokenRow = res.items().stream().filter(i -> i.equipmentId() == 10L).findFirst().orElseThrow();
    assertEquals(false, brokenRow.operational());
  }

  @Test
  void allNonOperationalMeansZeroPercent() {
    Instant from = NOW.minusSeconds(3600);
    Equipment broken = equipment(10L, "OSC-002");
    broken.setMaintenanceStatus(MaintenanceStatus.OUT_OF_SERVICE);
    when(equipment.findAll()).thenReturn(List.of(broken));
    when(sessions.findOverlapping(from, NOW)).thenReturn(List.of());

    var res = service.utilization(from, NOW, null);

    assertEquals(0.0, res.utilizationPercent());
  }

  @Test
  void sensorReliabilityIsFreshShare() {
    when(sensorEvents.countByStatusInWindow(any(), any()))
        .thenReturn(List.of(statusCount(SensorStatus.IDLE, 8), statusCount(SensorStatus.FAULT, 2)));
    when(sensorEvents.countByEquipmentInWindow(any(), any()))
        .thenReturn(List.of(equipCount("OSC-001", 10)));
    when(sensorEvents.countByStaleTrueAndOccurredAtBetween(any(), any())).thenReturn(1L);

    var res = service.sensorAnalytics(NOW.minusSeconds(3600), NOW, null);

    assertEquals(10L, res.total());
    assertEquals(2L, res.faults());
    assertEquals(0.9, res.reliability());
    assertEquals(1, res.byEquipment().size());
  }

  @Test
  void sensorReliabilityNullWithoutEvents() {
    when(sensorEvents.countByStatusInWindow(any(), any())).thenReturn(List.of());
    when(sensorEvents.countByEquipmentInWindow(any(), any())).thenReturn(List.of());
    when(sensorEvents.countByStaleTrueAndOccurredAtBetween(any(), any())).thenReturn(0L);

    var res = service.sensorAnalytics(NOW.minusSeconds(3600), NOW, null);

    assertEquals(0L, res.total());
    assertNull(res.reliability());
  }

  @Test
  void bookingAnalyticsGroupsByStatusDayAndEquipment() {
    Instant from = NOW.minusSeconds(2 * 86_400);
    when(bookings.findRowsInWindow(from, NOW))
        .thenReturn(
            List.of(
                bookingRow(NOW.minusSeconds(86_400), BookingStatus.CONFIRMED, 9L, "OSC-001"),
                bookingRow(NOW.minusSeconds(3600), BookingStatus.CANCELLED, 9L, "OSC-001"),
                bookingRow(NOW.minusSeconds(3600), BookingStatus.CONFIRMED, 10L, "CENT-001")));

    var res = service.bookingAnalytics(from, NOW, null);

    assertEquals(3L, res.total());
    assertEquals(2L, res.byStatus().get("CONFIRMED"));
    assertEquals(1L, res.cancelled());
    assertEquals(2, res.byDay().size());
    assertEquals(2, res.byEquipment().size());
    assertEquals("OSC-001", res.byEquipment().get(0).equipmentCode());
    assertEquals(2L, res.byEquipment().get(0).count());

    var filtered = service.bookingAnalytics(from, NOW, 10L);
    assertEquals(1L, filtered.total());
  }

  @Test
  void mySummarySeesOnlyOwnRows() {
    User stu = user(7L, "stu");
    when(users.findByUsername("stu")).thenReturn(Optional.of(stu));
    when(bookings.countByOwnerUsernameAndStatus(any(), any())).thenReturn(0L);
    when(bookings.countByOwnerUsernameAndStatus("stu", BookingStatus.CONFIRMED)).thenReturn(2L);
    Equipment item = equipment(9L, "OSC-001");
    when(sessions.findOverlappingForUser(eq(7L), any(), any()))
        .thenReturn(List.of(session(1L, item, NOW.minusSeconds(3600), NOW)));

    var res = service.mySummary("stu");

    assertEquals("stu", res.username());
    assertEquals(2L, res.bookings().total());
    assertEquals(2L, res.bookings().confirmed());
    assertEquals(3600L, res.usage().totalSeconds());
    verify(sessions).findOverlappingForUser(eq(7L), any(), any());
  }

  @Test
  void conflictAttemptsCountsAudits() {
    Instant from = NOW.minusSeconds(86_400);
    when(audits.countByEventTypeAndCreatedAtBetween(any(), any(), any())).thenReturn(3L);

    assertEquals(3L, service.conflictAttempts(from, NOW));
  }

  private static Equipment equipment(Long id, String code) {
    Equipment e = new Equipment();
    e.setEquipmentCode(code);
    e.setName("Scope");
    e.setCategory("Electronics");
    try {
      var idField = Equipment.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(e, id);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return e;
  }

  private static User user(Long id, String username) {
    User u = new User();
    u.setUsername(username);
    u.setEmail(username + "@example.com");
    try {
      var idField = User.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(u, id);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return u;
  }

  private static UsageSession session(Long id, Equipment item, Instant start, Instant end) {
    UsageSession s = new UsageSession();
    s.setEquipment(item);
    s.setStartedAt(start);
    s.setEndedAt(end);
    s.setStatus(end == null ? UsageStatus.ACTIVE : UsageStatus.COMPLETED);
    s.setDurationSeconds(end == null ? null : end.getEpochSecond() - start.getEpochSecond());
    try {
      var idField = UsageSession.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(s, id);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return s;
  }

  private static BookingRepository.BookingRow bookingRow(
      Instant created, BookingStatus status, Long equipmentId, String code) {
    return new BookingRepository.BookingRow() {
      public Instant getCreated() {
        return created;
      }

      public BookingStatus getStatus() {
        return status;
      }

      public Long getEquipmentId() {
        return equipmentId;
      }

      public String getCode() {
        return code;
      }
    };
  }

  private static SensorEventRepository.StatusCount statusCount(SensorStatus status, long total) {
    return new SensorEventRepository.StatusCount() {
      public SensorStatus getStatus() {
        return status;
      }

      public long getTotal() {
        return total;
      }
    };
  }

  private static SensorEventRepository.EquipmentCount equipCount(String code, long total) {
    return new SensorEventRepository.EquipmentCount() {
      public String getCode() {
        return code;
      }

      public long getTotal() {
        return total;
      }
    };
  }
}
