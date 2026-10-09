package com.labmarket.service;

import com.labmarket.dto.AlertResponse;
import com.labmarket.dto.PagedResponse;
import com.labmarket.dto.ResolveAlertRequest;
import com.labmarket.entity.Alert;
import com.labmarket.entity.AlertStatus;
import com.labmarket.entity.AlertType;
import com.labmarket.entity.Booking;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.Role;
import com.labmarket.entity.UsageSession;
import com.labmarket.entity.UsageStatus;
import com.labmarket.entity.User;
import com.labmarket.exception.ConflictException;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.mapper.AlertMapper;
import com.labmarket.repository.AlertRepository;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.UsageSessionRepository;
import com.labmarket.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alert queries and staff resolution. Resolving an OVERDUE alert also remediates
 * the booking: an open usage session is closed (COMPLETED) like a checkout,
 * otherwise the never-used booking is CANCELLED — and the equipment returns to
 * AVAILABLE unless staff/sensor moved it elsewhere.
 */
@Service
public class AlertService {

  private static final Logger log = LoggerFactory.getLogger(AlertService.class);

  private static final Set<String> STAFF_ROLES = Set.of("VENDOR", "ADMIN");

  private final AlertRepository alerts;
  private final BookingRepository bookings;
  private final EquipmentRepository equipment;
  private final UsageSessionRepository sessions;
  private final UserRepository users;
  private final AuditService audit;
  private final AlertMapper mapper;
  private final Clock clock;

  public AlertService(
      AlertRepository alerts,
      BookingRepository bookings,
      EquipmentRepository equipment,
      UsageSessionRepository sessions,
      UserRepository users,
      AuditService audit,
      AlertMapper mapper,
      Clock clock) {
    this.alerts = alerts;
    this.bookings = bookings;
    this.equipment = equipment;
    this.sessions = sessions;
    this.users = users;
    this.audit = audit;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PagedResponse<AlertResponse> list(AlertType type, AlertStatus status, Pageable pageable) {
    Page<Alert> page =
        type == null && status == null
            ? alerts.findAll(pageable)
            : type == null
                ? alerts.findByStatus(status, pageable)
                : status == null
                    ? alerts.findByTypeAndStatus(type, AlertStatus.OPEN, pageable)
                    : alerts.findByTypeAndStatus(type, status, pageable);
    return toPaged(page);
  }

  @Transactional(readOnly = true)
  public PagedResponse<AlertResponse> overdue(Pageable pageable) {
    return toPaged(alerts.findByTypeAndStatus(AlertType.OVERDUE, AlertStatus.OPEN, pageable));
  }

  @Transactional
  public AlertResponse resolve(String resolverUsername, Long alertId, ResolveAlertRequest req) {
    Alert alert =
        alerts
            .findById(alertId)
            .orElseThrow(() -> new ResourceNotFoundException("Alert with id " + alertId + " not found"));
    if (alert.getStatus() == AlertStatus.RESOLVED) {
      throw new ConflictException("Alert " + alertId + " is already resolved");
    }
    User resolver = loadStaff(resolverUsername);
    Instant now = clock.instant();
    Booking booking = alert.getBooking();
    String outcome = "no booking change (already " + booking.getStatus() + ")";
    if (booking.getStatus() == BookingStatus.OVERDUE) {
      Optional<UsageSession> session = sessions.findByBookingId(booking.getId());
      if (session.isPresent() && session.get().getEndedAt() == null) {
        UsageSession s = session.get();
        s.setEndedAt(now);
        s.setDurationSeconds(Math.max(0, Duration.between(s.getStartedAt(), now).getSeconds()));
        s.setStatus(UsageStatus.COMPLETED);
        booking.setStatus(BookingStatus.COMPLETED);
        audit.log(
            AuditService.USAGE_ENDED, resolverUsername, "USAGE", s.getId(),
            "Usage force-closed by overdue resolution after " + s.getDurationSeconds() + "s");
        outcome = "booking COMPLETED, session closed";
      } else {
        booking.setStatus(BookingStatus.CANCELLED);
        outcome = "booking CANCELLED (never used)";
      }
      Equipment item =
          equipment.findWithLockById(booking.getEquipment().getId()).orElseGet(booking::getEquipment);
      if (item.getCurrentStatus() == EquipmentStatus.OVERDUE
          || item.getCurrentStatus() == EquipmentStatus.IN_USE) {
        item.setCurrentStatus(EquipmentStatus.AVAILABLE);
      }
    }
    alert.setStatus(AlertStatus.RESOLVED);
    alert.setResolutionNote(req == null ? null : req.note());
    alert.setResolvedBy(resolver);
    alert.setResolvedAt(now);
    audit.log(
        AuditService.ALERT_RESOLVED, resolverUsername, "ALERT", alertId,
        "Resolved: " + outcome);
    log.info("Alert {} resolved by '{}': {}", alertId, resolverUsername, outcome);
    return mapper.toResponse(alert);
  }

  private User loadStaff(String username) {
    User user =
        users
            .findByUsername(username)
            .orElseThrow(() -> new AccessDeniedException("Access denied"));
    if (user.getRoles().stream().map(Role::getName).noneMatch(STAFF_ROLES::contains)) {
      throw new AccessDeniedException("Access denied");
    }
    return user;
  }

  private PagedResponse<AlertResponse> toPaged(Page<Alert> page) {
    return new PagedResponse<>(
        page.getContent().stream().map(mapper::toResponse).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
