package com.labmarket.service;

import com.labmarket.dto.ManualUsageRequest;
import com.labmarket.dto.PagedResponse;
import com.labmarket.dto.UsageLogResponse;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.Role;
import com.labmarket.entity.UsageSession;
import com.labmarket.entity.UsageSource;
import com.labmarket.entity.UsageStatus;
import com.labmarket.entity.User;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.mapper.UsageLogMapper;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.UsageSessionRepository;
import com.labmarket.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usage history.
 *
 * <ul>
 *   <li>Students see only their own rows; VENDOR/ADMIN see everything (scoped by filters).</li>
 *   <li>Single rows: owner, staff or admin — anyone else gets 403.</li>
 *   <li>There is intentionally NO update/delete path: COMPLETED rows are historical.</li>
 *   <li>Staff/admin may record MANUAL historical usage (walk-ins); duration is computed.</li>
 * </ul>
 */
@Service
public class UsageLogService {

  private static final Logger log = LoggerFactory.getLogger(UsageLogService.class);

  private static final Set<String> STAFF_ROLES = Set.of("VENDOR", "ADMIN");

  private final UsageSessionRepository sessions;
  private final EquipmentRepository equipment;
  private final UserRepository users;
  private final AuditService audit;
  private final UsageLogMapper mapper;

  public UsageLogService(
      UsageSessionRepository sessions,
      EquipmentRepository equipment,
      UserRepository users,
      AuditService audit,
      UsageLogMapper mapper) {
    this.sessions = sessions;
    this.equipment = equipment;
    this.users = users;
    this.audit = audit;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public PagedResponse<UsageLogResponse> list(
      String username,
      Long equipmentId,
      Long userId,
      UsageStatus status,
      UsageSource source,
      String laboratory,
      Instant from,
      Instant to,
      Pageable pageable) {
    requireValidWindow(from, to);
    User viewer = loadUser(username);
    Long effectiveUser = userId;
    if (!isStaff(viewer)) {
      if (userId != null && !userId.equals(viewer.getId())) {
        throw new AccessDeniedException("Access denied");
      }
      effectiveUser = viewer.getId();
    }
    return toPaged(
        sessions.search(
            equipmentId, effectiveUser, status, source, blankToNull(laboratory), from, to, pageable));
  }

  @Transactional(readOnly = true)
  public PagedResponse<UsageLogResponse> my(
      String username,
      Long equipmentId,
      UsageStatus status,
      UsageSource source,
      Instant from,
      Instant to,
      Pageable pageable) {
    requireValidWindow(from, to);
    User viewer = loadUser(username);
    return toPaged(
        sessions.search(equipmentId, viewer.getId(), status, source, null, from, to, pageable));
  }

  @Transactional(readOnly = true)
  public UsageLogResponse get(String username, Long id) {
    User viewer = loadUser(username);
    UsageSession s =
        sessions
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Usage record with id " + id + " not found"));
    if (!isStaff(viewer) && !s.getUser().getId().equals(viewer.getId())) {
      throw new AccessDeniedException("Access denied");
    }
    return mapper.toResponse(s);
  }

  /**
   * Records historical MANUAL usage. Staff/admin only (see controller).
   * The row is born COMPLETED with a computed duration — it can never be edited afterwards.
   */
  @Transactional
  public UsageLogResponse createManual(String staffUsername, ManualUsageRequest req) {
    Instant start = req.startTime().truncatedTo(ChronoUnit.MILLIS);
    Instant end = req.endTime().truncatedTo(ChronoUnit.MILLIS);
    if (!end.isAfter(start)) {
      throw new IllegalArgumentException("End time must be after start time");
    }
    if (end.isAfter(Instant.now())) {
      throw new IllegalArgumentException("Manual usage records historical use — end time must not be in the future");
    }
    Equipment item =
        equipment
            .findById(req.equipmentId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Equipment with id " + req.equipmentId() + " not found"));
    User user =
        users
            .findById(req.userId())
            .orElseThrow(() -> new ResourceNotFoundException("User with id " + req.userId() + " not found"));
    UsageSession s = new UsageSession();
    s.setBooking(null);
    s.setEquipment(item);
    s.setUser(user);
    s.setStartedAt(start);
    s.setEndedAt(end);
    s.setDurationSeconds(Math.max(0, Duration.between(start, end).getSeconds()));
    s.setSource(UsageSource.MANUAL);
    s.setStatus(UsageStatus.COMPLETED);
    s.setNote(req.note());
    UsageSession saved = sessions.save(s);
    audit.log(
        AuditService.USAGE_STARTED, staffUsername, "USAGE", saved.getId(),
        "Manual usage recorded for '" + user.getUsername() + "' on '" + item.getEquipmentCode() + "'");
    audit.log(
        AuditService.USAGE_ENDED, staffUsername, "USAGE", saved.getId(),
        "Manual usage closed after " + saved.getDurationSeconds() + "s");
    log.info("Manual usage {} recorded by '{}'", saved.getId(), staffUsername);
    return mapper.toResponse(saved);
  }

  private void requireValidWindow(Instant from, Instant to) {
    if (from != null && to != null && !to.isAfter(from)) {
      throw new IllegalArgumentException("Query window requires 'from' before 'to'");
    }
  }

  private User loadUser(String username) {
    return users
        .findByUsername(username)
        .orElseThrow(() -> new AccessDeniedException("Access denied"));
  }

  private static boolean isStaff(User user) {
    return user.getRoles().stream().map(Role::getName).anyMatch(STAFF_ROLES::contains);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private PagedResponse<UsageLogResponse> toPaged(Page<UsageSession> page) {
    return new PagedResponse<>(
        page.getContent().stream().map(mapper::toResponse).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
