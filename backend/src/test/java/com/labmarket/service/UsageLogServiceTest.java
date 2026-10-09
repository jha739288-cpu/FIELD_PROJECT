package com.labmarket.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labmarket.dto.ManualUsageRequest;
import com.labmarket.dto.UsageLogResponse;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.Role;
import com.labmarket.entity.UsageSession;
import com.labmarket.entity.UsageSource;
import com.labmarket.entity.UsageStatus;
import com.labmarket.entity.User;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.mapper.UsageLogMapper;
import com.labmarket.repository.AuditEventRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.UsageSessionRepository;
import com.labmarket.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

/** Pure unit tests for usage-history rules (no Spring context). */
@ExtendWith(MockitoExtension.class)
class UsageLogServiceTest {

  @Mock private UsageSessionRepository sessions;
  @Mock private EquipmentRepository equipment;
  @Mock private UserRepository users;
  @Mock private AuditEventRepository auditEvents;

  private UsageLogService service;

  @BeforeEach
  void setUp() {
    service =
        new UsageLogService(
            sessions, equipment, users, new AuditService(auditEvents), new UsageLogMapper());
  }

  @Test
  void studentListIsForcedToOwnId() {
    User stu = user(1L, "stu", "USER");
    when(users.findByUsername("stu")).thenReturn(Optional.of(stu));
    when(sessions.search(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));

    service.list("stu", null, null, null, null, null, null, null, Pageable.unpaged());

    verify(sessions)
        .search(eq(null), eq(1L), eq(null), eq(null), eq(null), eq(null), eq(null), any());
  }

  @Test
  void studentFilteringByAnotherUserIsForbidden() {
    User stu = user(1L, "stu", "USER");
    when(users.findByUsername("stu")).thenReturn(Optional.of(stu));

    assertThrows(
        AccessDeniedException.class,
        () ->
            service.list("stu", null, 99L, null, null, null, null, null, Pageable.unpaged()));
  }

  @Test
  void staffListPassesAllFiltersThrough() {
    User staff = user(2L, "stf", "VENDOR");
    when(users.findByUsername("stf")).thenReturn(Optional.of(staff));
    when(sessions.search(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));

    service.list(
        "stf", 7L, 1L, UsageStatus.COMPLETED, UsageSource.MANUAL, "Lab A", null, null,
        Pageable.unpaged());

    verify(sessions)
        .search(
            eq(7L), eq(1L), eq(UsageStatus.COMPLETED), eq(UsageSource.MANUAL), eq("Lab A"),
            eq(null), eq(null), any());
  }

  @Test
  void getOtherUsersRecordIsForbidden() {
    User stuB = user(2L, "stuB", "USER");
    UsageSession s = session(10L, user(1L, "stuA", "USER"));
    when(users.findByUsername("stuB")).thenReturn(Optional.of(stuB));
    when(sessions.findById(10L)).thenReturn(Optional.of(s));

    assertThrows(AccessDeniedException.class, () -> service.get("stuB", 10L));
  }

  @Test
  void getMissingIsNotFound() {
    when(users.findByUsername("stf")).thenReturn(Optional.of(user(2L, "stf", "VENDOR")));
    when(sessions.findById(99L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> service.get("stf", 99L));
  }

  @Test
  void manualComputesDurationAuditsTwiceAndIsCompleted() {
    Equipment item = new Equipment();
    item.setEquipmentCode("OSC-001");
    User stu = user(1L, "stu", "USER");
    Instant start = Instant.now().minusSeconds(3700);
    Instant end = Instant.now().minusSeconds(100);
    when(equipment.findById(7L)).thenReturn(Optional.of(item));
    when(users.findById(1L)).thenReturn(Optional.of(stu));
    when(sessions.save(any(UsageSession.class))).thenAnswer(i -> i.getArgument(0));

    UsageLogResponse res =
        service.createManual("stf", new ManualUsageRequest(7L, 1L, start, end, "walk-in"));

    assertEquals(UsageSource.MANUAL, res.source());
    assertEquals(UsageStatus.COMPLETED, res.status());
    assertEquals(3600, res.durationSeconds());
    verify(auditEvents, org.mockito.Mockito.times(2)).save(any());
  }

  @Test
  void manualFutureEndIsRejected() {
    Instant start = Instant.now().minusSeconds(3600);
    Instant end = Instant.now().plusSeconds(3600);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.createManual("stf", new ManualUsageRequest(7L, 1L, start, end, null)));
  }

  @Test
  void manualEndBeforeStartIsRejected() {
    Instant point = Instant.now().minusSeconds(3600);

    assertThrows(
        IllegalArgumentException.class,
        () -> service.createManual("stf", new ManualUsageRequest(7L, 1L, point, point, null)));
  }

  @Test
  void manualMissingEquipmentIsNotFound() {
    when(equipment.findById(77L)).thenReturn(Optional.empty());
    Instant start = Instant.now().minusSeconds(7200);
    Instant end = Instant.now().minusSeconds(3600);

    assertThrows(
        ResourceNotFoundException.class,
        () -> service.createManual("stf", new ManualUsageRequest(77L, 1L, start, end, null)));
  }

  private static User user(Long id, String username, String... roleNames) {
    User u = new User();
    u.setUsername(username);
    u.setEmail(username + "@example.com");
    u.setPasswordHash("$2a$10$testhashfortests0000000000000000000000000000");
    for (String r : roleNames) {
      u.getRoles().add(new Role(r, r));
    }
    try {
      var idField = User.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(u, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    return u;
  }

  private static UsageSession session(Long id, User owner) {
    UsageSession s = new UsageSession();
    Equipment item = new Equipment();
    item.setEquipmentCode("OSC-001");
    item.setName("Oscilloscope");
    s.setEquipment(item);
    s.setUser(owner);
    s.setStartedAt(Instant.now().minusSeconds(3600));
    s.setSource(UsageSource.QR_CHECKIN);
    s.setStatus(UsageStatus.COMPLETED);
    try {
      var idField = UsageSession.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(s, id);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return s;
  }
}
