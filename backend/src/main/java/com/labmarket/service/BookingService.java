package com.labmarket.service;

import com.labmarket.dto.AvailabilityResponse;
import com.labmarket.dto.BookedPeriodResponse;
import com.labmarket.dto.BookingCreateRequest;
import com.labmarket.dto.BookingResponse;
import com.labmarket.dto.BookingSlotResponse;
import com.labmarket.dto.CalendarResponse;
import com.labmarket.dto.CheckInResponse;
import com.labmarket.dto.CheckOutResponse;
import com.labmarket.dto.EquipmentScheduleResponse;
import com.labmarket.dto.PagedResponse;
import com.labmarket.dto.QrTokenResponse;
import com.labmarket.dto.TimeSlotResponse;
import com.labmarket.dto.UsageSessionResponse;
import com.labmarket.entity.Booking;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.QrToken;
import com.labmarket.entity.Role;
import com.labmarket.entity.UsageSession;
import com.labmarket.entity.UsageSource;
import com.labmarket.entity.UsageStatus;
import com.labmarket.entity.User;
import com.labmarket.exception.ConflictException;
import com.labmarket.exception.GoneException;
import com.labmarket.exception.ResourceNotFoundException;
import com.labmarket.mapper.BookingMapper;
import com.labmarket.repository.BookingRepository;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.QrTokenRepository;
import com.labmarket.repository.UsageSessionRepository;
import com.labmarket.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booking rules.
 *
 * <ul>
 *   <li>Ranges are half-open {@code [start, end)}: back-to-back bookings are legal.</li>
 *   <li>Overlap safety: the EQUIPMENT row is locked ({@code FOR UPDATE}) inside the
 *       transaction <em>before</em> the overlap check, so concurrent requests for the
 *       same item serialize (Oracle has no exclusion constraints).</li>
 *   <li>Blocking statuses: CONFIRMED, CHECKED_IN, OVERDUE. PENDING requests may coexist
 *       until staff confirm one of them — confirmation re-checks.</li>
 *   <li>Only OPERATIONAL, non-retired equipment (not MAINTENANCE / SENSOR_OFFLINE) is bookable.</li>
 *   <li>Visibility: owners see their own bookings; LAB_STAFF / ADMIN see everything.</li>
 * </ul>
 */
@Service
public class BookingService {

  private static final Logger log = LoggerFactory.getLogger(BookingService.class);

  /** Booking states that block a new or confirmed reservation on the same equipment. */
  public static final Set<BookingStatus> BLOCKING =
      EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.OVERDUE);
  /** Booking states shown on schedule/calendar views (everything not final or rejected). */
  private static final Set<BookingStatus> VISIBLE =
      EnumSet.of(
          BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.OVERDUE);

  /** Longest window the calendar APIs accept — protects the database from unbounded scans. */
  private static final Duration MAX_WINDOW = Duration.ofDays(93);

  /** Check-in opens this far before the booking start. */
  private static final Duration CHECK_IN_EARLY = Duration.ofMinutes(30);

  /** QR tokens stay valid this far past the booking end (covers late check-ins, never checkout). */
  private static final Duration QR_GRACE_AFTER_END = Duration.ofMinutes(15);

  private static final SecureRandom RANDOM = new SecureRandom();

  private static final Set<String> STAFF_ROLES = Set.of("LAB_STAFF", "ADMIN");

  private final BookingRepository bookings;
  private final EquipmentRepository equipment;
  private final UserRepository users;
  private final QrTokenRepository qrTokens;
  private final UsageSessionRepository sessions;
  private final AuditService audit;
  private final BookingMapper mapper;
  private final Counter conflictCounter;

  public BookingService(
      BookingRepository bookings,
      EquipmentRepository equipment,
      UserRepository users,
      QrTokenRepository qrTokens,
      UsageSessionRepository sessions,
      AuditService audit,
      BookingMapper mapper,
      MeterRegistry registry) {
    this.bookings = bookings;
    this.equipment = equipment;
    this.users = users;
    this.qrTokens = qrTokens;
    this.sessions = sessions;
    this.audit = audit;
    this.mapper = mapper;
    this.conflictCounter = registry.counter("booking.conflicts");
  }

  /** Any authenticated user books → PENDING, after time/order/future/equipment/conflict checks. */
  @Transactional
  public BookingResponse create(String username, BookingCreateRequest req) {
    // Normalized first: DB TIMESTAMP columns round sub-millisecond fractions on write,
    // which would otherwise create phantom micro-overlaps between back-to-back bookings.
    Instant start = req.startTime().truncatedTo(ChronoUnit.MILLIS);
    Instant end = req.endTime().truncatedTo(ChronoUnit.MILLIS);
    requireValidRange(start, end);
    User owner = loadUser(username);
    // Lock first: concurrent creates for the same equipment serialize here.
    Equipment item = lockEquipment(req.equipmentId());
    requireBookable(item);
    requireNoOverlap(item, start, end, username);
    Booking b = new Booking();
    b.setEquipment(item);
    b.setOwner(owner);
    b.setStartTime(start);
    b.setEndTime(end);
    b.setPurpose(req.purpose());
    b.setStatus(BookingStatus.PENDING);
    Booking saved = bookings.save(b);
    log.info(
        "Booking {} created (PENDING) by '{}' for equipment '{}'",
        saved.getId(), username, item.getEquipmentCode());
    return mapper.toResponse(saved);
  }

  /** Owner sees own; staff/admin see all (optional equipment/status filters). */
  @Transactional(readOnly = true)
  public PagedResponse<BookingResponse> list(
      String username, Long equipmentId, BookingStatus status, Pageable pageable) {
    User viewer = loadUser(username);
    Page<Booking> page =
        isStaff(viewer)
            ? bookings.search(equipmentId, status, pageable)
            : ownBookings(viewer, status, pageable);
    return toPaged(page);
  }

  /** Owner's own bookings, optional status filter. */
  @Transactional(readOnly = true)
  public PagedResponse<BookingResponse> my(String username, BookingStatus status, Pageable pageable) {
    User viewer = loadUser(username);
    return toPaged(ownBookings(viewer, status, pageable));
  }

  /** Owner, staff or admin. Anyone else gets 403 (no existence leak beyond that). */
  @Transactional(readOnly = true)
  public BookingResponse get(String username, Long id) {
    User viewer = loadUser(username);
    Booking b = findOrThrow(id);
    if (!isStaff(viewer) && !b.getOwner().getUsername().equals(username)) {
      throw new AccessDeniedException("Access denied");
    }
    return mapper.toResponse(b);
  }

  /** Owner (while PENDING/CONFIRMED) or staff/admin. Everything else → 409/403. */
  @Transactional
  public BookingResponse cancel(String username, Long id) {
    User viewer = loadUser(username);
    Booking b = findOrThrow(id);
    boolean owner = b.getOwner().getUsername().equals(username);
    if (!owner && !isStaff(viewer)) {
      throw new AccessDeniedException("Access denied");
    }
    if (b.getStatus() != BookingStatus.PENDING && b.getStatus() != BookingStatus.CONFIRMED) {
      throw new ConflictException(
          "Booking " + id + " cannot be cancelled while it is " + b.getStatus());
    }
    b.setStatus(BookingStatus.CANCELLED);
    log.info("Booking {} cancelled by '{}'", id, username);
    return mapper.toResponse(b);
  }

  /** Staff/admin only (see controller). PENDING → CONFIRMED after a fresh overlap check. */
  @Transactional
  public BookingResponse confirm(String username, Long id) {
    Booking b = findOrThrow(id);
    if (b.getStatus() != BookingStatus.PENDING) {
      throw new ConflictException(
          "Booking " + id + " cannot be confirmed while it is " + b.getStatus());
    }
    Equipment item = lockEquipment(b.getEquipment().getId());
    requireNoOverlap(item, b.getStartTime(), b.getEndTime(), username);
    b.setStatus(BookingStatus.CONFIRMED);
    log.info("Booking {} confirmed", id);
    return mapper.toResponse(b);
  }

  /** Staff/admin only (see controller). PENDING → REJECTED. */
  @Transactional
  public BookingResponse reject(String username, Long id) {
    Booking b = findOrThrow(id);
    if (b.getStatus() != BookingStatus.PENDING) {
      throw new ConflictException(
          "Booking " + id + " cannot be rejected while it is " + b.getStatus());
    }
    b.setStatus(BookingStatus.REJECTED);
    log.info("Booking {} rejected", id);
    return mapper.toResponse(b);
  }

  // --------------------------------------------------------------------------
  // QR check-in / check-out (server-validated; frontend-supplied ids are never trusted)
  // --------------------------------------------------------------------------

  /**
   * Issues a single-use opaque check-in token for a CONFIRMED booking.
   * Caller must own the booking or be staff/admin. Prior unused tokens for the
   * booking are invalidated. The raw token is returned once and never stored.
   */
  @Transactional
  public QrTokenResponse generateQr(String username, Long bookingId) {
    User caller = loadUser(username);
    Booking b = findOrThrow(bookingId);
    if (!isOwner(b, username) && !isStaff(caller)) {
      throw new AccessDeniedException("Access denied");
    }
    if (b.getStatus() != BookingStatus.CONFIRMED) {
      throw new ConflictException(
          "QR codes can only be generated for CONFIRMED bookings (current: " + b.getStatus() + ")");
    }
    if (!b.getEndTime().isAfter(Instant.now())) {
      throw new ConflictException("QR codes cannot be generated for past bookings");
    }
    qrTokens.deleteAll(qrTokens.findByBookingIdAndUsedFalse(bookingId));
    String raw = randomToken();
    QrToken token = new QrToken();
    token.setBooking(b);
    token.setTokenHash(sha256Hex(raw));
    token.setExpiresAt(b.getEndTime().plus(QR_GRACE_AFTER_END));
    qrTokens.save(token);
    audit.log(
        AuditService.QR_GENERATED,
        username,
        "BOOKING",
        bookingId,
        "QR token issued, expires " + token.getExpiresAt());
    log.info("QR token issued for booking {} by '{}'", bookingId, username);
    return new QrTokenResponse(raw, bookingId, token.getExpiresAt());
  }

  /**
   * Checks in by scanning a QR token. The token is the ONLY input: booking,
   * equipment and owner are all resolved server-side. Every rejection is audited
   * in an independent transaction. Success flips booking → CHECKED_IN,
   * equipment → IN_USE and opens a usage session — atomically.
   */
  @Transactional
  public CheckInResponse checkIn(String username, String rawToken) {
    String hash = sha256Hex(rawToken == null ? "" : rawToken.trim());
    QrToken token = qrTokens.findByTokenHash(hash).orElse(null);
    if (token == null) {
      auditFail(username, null, "unknown QR token");
      throw new ResourceNotFoundException("Invalid QR code");
    }
    Long bookingId = token.getBooking().getId();
    if (token.isUsed()) {
      auditFail(username, bookingId, "QR token already used");
      throw new ConflictException("QR code has already been used");
    }
    Instant now = Instant.now();
    if (!token.getExpiresAt().isAfter(now)) {
      auditFail(username, bookingId, "QR token expired");
      throw new GoneException("QR code has expired");
    }
    Booking b = token.getBooking();
    if (!isOwner(b, username)) {
      auditFail(username, bookingId, "wrong user for booking owned by " + b.getOwner().getUsername());
      throw new AccessDeniedException("Access denied");
    }
    if (b.getStatus() != BookingStatus.CONFIRMED) {
      auditFail(username, bookingId, "booking is " + b.getStatus() + ", not CONFIRMED");
      throw new ConflictException(
          "Booking " + bookingId + " is " + b.getStatus() + " — check-in requires a CONFIRMED booking");
    }
    if (now.isBefore(b.getStartTime().minus(CHECK_IN_EARLY))) {
      auditFail(username, bookingId, "too early for booking starting " + b.getStartTime());
      throw new ConflictException("Check-in opens 30 minutes before the booking start");
    }
    if (!now.isBefore(b.getEndTime())) {
      auditFail(username, bookingId, "booking window ended " + b.getEndTime());
      throw new ConflictException("Booking window has passed");
    }
    Equipment item = lockEquipment(b.getEquipment().getId());
    if (!isBookable(item)) {
      auditFail(username, bookingId, "equipment " + item.getEquipmentCode() + " not bookable");
      throw new ConflictException(
          "Equipment '" + item.getEquipmentCode() + "' is not available for check-in ("
              + item.getCurrentStatus() + " / " + item.getMaintenanceStatus() + ")");
    }
    if (sessions.findByBookingId(bookingId).isPresent()) {
      auditFail(username, bookingId, "usage session already open");
      throw new ConflictException("Booking " + bookingId + " is already checked in");
    }
    token.setUsed(true);
    token.setUsedAt(now);
    b.setStatus(BookingStatus.CHECKED_IN);
    item.setCurrentStatus(EquipmentStatus.IN_USE);
    UsageSession session = new UsageSession();
    session.setBooking(b);
    session.setEquipment(item);
    session.setUser(b.getOwner());
    session.setStartedAt(now);
    session.setSource(UsageSource.QR_CHECKIN);
    session.setStatus(UsageStatus.ACTIVE);
    sessions.save(session);
    audit.log(
        AuditService.CHECKED_IN, username, "BOOKING", bookingId, "Checked in, session " + session.getId());
    audit.log(
        AuditService.USAGE_STARTED,
        username,
        "USAGE",
        session.getId(),
        "Usage started on equipment '" + item.getEquipmentCode() + "'");
    log.info("Booking {} checked in by '{}'", bookingId, username);
    return new CheckInResponse(mapper.toResponse(b), toSession(session));
  }

  /**
   * Checks out: closes the usage session with a computed duration, flips booking
   * → COMPLETED and returns the equipment to AVAILABLE (unless staff moved it
   * elsewhere meanwhile, which is never clobbered). Owner or staff/admin.
   */
  @Transactional
  public CheckOutResponse checkOut(String username, Long bookingId) {
    User caller = loadUser(username);
    Booking b = findOrThrow(bookingId);
    if (!isOwner(b, username) && !isStaff(caller)) {
      throw new AccessDeniedException("Access denied");
    }
    if (b.getStatus() != BookingStatus.CHECKED_IN) {
      throw new ConflictException(
          "Booking " + bookingId + " cannot be checked out while it is " + b.getStatus());
    }
    UsageSession session =
        sessions
            .findByBookingId(bookingId)
            .orElseThrow(
                () -> new ConflictException("Booking " + bookingId + " has no active usage session"));
    if (session.getEndedAt() != null) {
      throw new ConflictException("Booking " + bookingId + " is already checked out");
    }
    Instant now = Instant.now();
    session.setEndedAt(now);
    session.setDurationSeconds(Math.max(0, Duration.between(session.getStartedAt(), now).getSeconds()));
    session.setStatus(UsageStatus.COMPLETED);
    b.setStatus(BookingStatus.COMPLETED);
    Equipment item = lockEquipment(b.getEquipment().getId());
    if (item.getCurrentStatus() == EquipmentStatus.IN_USE) {
      item.setCurrentStatus(EquipmentStatus.AVAILABLE);
    }
    audit.log(
        AuditService.CHECKED_OUT,
        username,
        "BOOKING",
        bookingId,
        "Checked out after " + session.getDurationSeconds() + "s, session " + session.getId());
    audit.log(
        AuditService.USAGE_ENDED,
        username,
        "USAGE",
        session.getId(),
        "Usage ended after " + session.getDurationSeconds() + "s");
    log.info("Booking {} checked out by '{}'", bookingId, username);
    return new CheckOutResponse(mapper.toResponse(b), toSession(session));
  }

  private void auditFail(String username, Long bookingId, String reason) {
    audit.log(AuditService.QR_VALIDATION_FAILED, username, "BOOKING", bookingId, reason);
  }

  private static boolean isOwner(Booking b, String username) {
    return b.getOwner().getUsername().equals(username);
  }

  private static String randomToken() {
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

  private static UsageSessionResponse toSession(UsageSession s) {
    return new UsageSessionResponse(
        s.getId(),
        s.getBooking().getId(),
        s.getEquipment().getId(),
        s.getUser().getUsername(),
        s.getStartedAt(),
        s.getEndedAt(),
        s.getDurationSeconds());
  }

  // --------------------------------------------------------------------------
  // Calendar views (read-only; reuse the same overlap semantics, no new rules)
  // --------------------------------------------------------------------------

  /** One equipment lane: identity, live status and its visible bookings in the window. */
  @Transactional(readOnly = true)
  public EquipmentScheduleResponse schedule(
      String username, Long equipmentId, Instant from, Instant to) {
    requireValidWindow(from, to);
    User viewer = loadUser(username);
    Equipment item = loadEquipment(equipmentId);
    boolean staff = isStaff(viewer);
    List<BookingSlotResponse> slots =
        bookings.findOverlapping(equipmentId, VISIBLE, from, to).stream()
            .map(b -> slot(b, username, staff))
            .toList();
    return new EquipmentScheduleResponse(
        item.getId(),
        item.getEquipmentCode(),
        item.getName(),
        item.getCurrentStatus(),
        item.getMaintenanceStatus(),
        from,
        to,
        slots);
  }

  /** Bookable flag, blocking periods, pending requests and the free gaps between them. */
  @Transactional(readOnly = true)
  public AvailabilityResponse availability(Long equipmentId, Instant from, Instant to) {
    requireValidWindow(from, to);
    Equipment item = loadEquipment(equipmentId);
    boolean bookable = isBookable(item);
    List<Booking> blockingBookings = bookings.findOverlapping(equipmentId, BLOCKING, from, to);
    List<Interval> blocking =
        merge(
            blockingBookings.stream()
                .map(b -> clip(b.getStartTime(), b.getEndTime(), from, to))
                .toList());
    List<BookedPeriodResponse> booked =
        blockingBookings.stream()
            .map(
                b ->
                    new BookedPeriodResponse(
                        b.getId(),
                        max(b.getStartTime(), from),
                        min(b.getEndTime(), to),
                        b.getStatus()))
            .toList();
    List<TimeSlotResponse> pending =
        bookings.findOverlapping(equipmentId, Set.of(BookingStatus.PENDING), from, to).stream()
            .map(b -> new TimeSlotResponse(max(b.getStartTime(), from), min(b.getEndTime(), to)))
            .toList();
    List<TimeSlotResponse> free = bookable ? gaps(blocking, from, to) : List.of();
    boolean maintenance = !bookable;
    String message =
        bookable
            ? null
            : "Equipment '" + item.getEquipmentCode() + "' is currently not bookable ("
                + item.getCurrentStatus() + " / " + item.getMaintenanceStatus() + ")";
    return new AvailabilityResponse(
        item.getId(),
        item.getEquipmentCode(),
        item.getName(),
        item.getCurrentStatus(),
        item.getMaintenanceStatus(),
        bookable,
        from,
        to,
        booked,
        pending,
        free,
        maintenance,
        message);
  }

  /** Calendar lanes for one item (when given) or every item, with per-viewer redaction. */
  @Transactional(readOnly = true)
  public CalendarResponse calendar(String username, Instant from, Instant to, Long equipmentId) {
    requireValidWindow(from, to);
    User viewer = loadUser(username);
    boolean staff = isStaff(viewer);
    List<Equipment> items =
        equipmentId == null ? equipment.findAll() : List.of(loadEquipment(equipmentId));
    List<Booking> found =
        equipmentId == null
            ? bookings.findAllOverlapping(VISIBLE, from, to)
            : bookings.findOverlapping(equipmentId, VISIBLE, from, to);
    Map<Long, List<Booking>> byEquipment = new LinkedHashMap<>();
    for (Equipment item : items) {
      byEquipment.put(item.getId(), new ArrayList<>());
    }
    for (Booking b : found) {
      byEquipment.computeIfAbsent(b.getEquipment().getId(), k -> new ArrayList<>()).add(b);
    }
    Map<Long, Equipment> index = new LinkedHashMap<>();
    for (Equipment item : items) {
      index.put(item.getId(), item);
    }
    for (Booking b : found) {
      // Lanes are already fetched, so this only fills gaps defensively.
      index.computeIfAbsent(b.getEquipment().getId(), k -> b.getEquipment());
    }
    List<EquipmentScheduleResponse> schedules = new ArrayList<>();
    for (Map.Entry<Long, Equipment> entry : index.entrySet()) {
      Equipment item = entry.getValue();
      List<BookingSlotResponse> slots =
          byEquipment.getOrDefault(entry.getKey(), List.of()).stream()
              .map(b -> slot(b, username, staff))
              .toList();
      schedules.add(
          new EquipmentScheduleResponse(
              item.getId(),
              item.getEquipmentCode(),
              item.getName(),
              item.getCurrentStatus(),
              item.getMaintenanceStatus(),
              from,
              to,
              slots));
    }
    return new CalendarResponse(from, to, schedules);
  }

  private BookingSlotResponse slot(Booking b, String viewer, boolean staff) {
    boolean owner = b.getOwner().getUsername().equals(viewer);
    String purpose = staff || owner ? b.getPurpose() : null;
    String name = staff || owner ? b.getOwner().getUsername() : null;
    return new BookingSlotResponse(
        b.getId(), b.getStartTime(), b.getEndTime(), b.getStatus(), purpose, name);
  }

  private void requireValidWindow(Instant from, Instant to) {
    if (from == null || to == null || !to.isAfter(from)) {
      throw new IllegalArgumentException("Query window requires 'from' before 'to'");
    }
    if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
      throw new IllegalArgumentException("Query window must not exceed 93 days");
    }
  }

  /** Half-open clipped interval, or null when it falls fully outside the window. */
  private static Interval clip(Instant start, Instant end, Instant from, Instant to) {
    Instant s = max(start, from);
    Instant e = min(end, to);
    return e.isAfter(s) ? new Interval(s, e) : null;
  }

  private static List<Interval> merge(List<Interval> intervals) {
    List<Interval> sorted =
        intervals.stream()
            .filter(i -> i != null)
            .sorted((a, b) -> a.s().compareTo(b.s()))
            .toList();
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

  private static List<TimeSlotResponse> gaps(List<Interval> blocking, Instant from, Instant to) {
    List<TimeSlotResponse> free = new ArrayList<>();
    Instant cursor = from;
    for (Interval i : blocking) {
      if (i.s().isAfter(cursor)) {
        free.add(new TimeSlotResponse(cursor, i.s()));
      }
      if (i.e().isAfter(cursor)) {
        cursor = i.e();
      }
    }
    if (cursor.isBefore(to)) {
      free.add(new TimeSlotResponse(cursor, to));
    }
    return free;
  }

  private record Interval(Instant s, Instant e) {}

  private static Instant max(Instant a, Instant b) {
    return a.isAfter(b) ? a : b;
  }

  private static Instant min(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
  }

  private Equipment loadEquipment(Long equipmentId) {
    return equipment
        .findById(equipmentId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Equipment with id " + equipmentId + " not found"));
  }

  private void requireValidRange(Instant start, Instant end) {
    if (!end.isAfter(start)) {
      throw new IllegalArgumentException("End time must be after start time");
    }
    if (!start.isAfter(Instant.now())) {
      throw new IllegalArgumentException("Start time must be in the future");
    }
  }

  private void requireBookable(Equipment item) {
    if (!isBookable(item)) {
      throw new ConflictException(
          "Equipment '" + item.getEquipmentCode() + "' is not bookable (" + item.getCurrentStatus()
              + " / " + item.getMaintenanceStatus() + ")");
    }
  }

  private static boolean isBookable(Equipment item) {
    return item.getMaintenanceStatus() == MaintenanceStatus.OPERATIONAL
        && item.getCurrentStatus() != EquipmentStatus.MAINTENANCE
        && item.getCurrentStatus() != EquipmentStatus.SENSOR_OFFLINE;
  }

  private void requireNoOverlap(Equipment item, Instant start, Instant end, String actor) {
    if (bookings.existsOverlap(item, BLOCKING, start, end)) {
      audit.log(
          AuditService.BOOKING_CONFLICT,
          actor,
          "EQUIPMENT",
          item.getId(),
          "Overlap rejected for '" + item.getEquipmentCode() + "' in [" + start + ", " + end + ")");
      conflictCounter.increment();
      throw new ConflictException(
          "Equipment '" + item.getEquipmentCode()
              + "' already has a confirmed booking overlapping the requested time range");
    }
  }

  private Equipment lockEquipment(Long equipmentId) {
    return equipment
        .findWithLockById(equipmentId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Equipment with id " + equipmentId + " not found"));
  }

  private Booking findOrThrow(Long id) {
    return bookings
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Booking with id " + id + " not found"));
  }

  private User loadUser(String username) {
    return users
        .findByUsername(username)
        .orElseThrow(() -> new AccessDeniedException("Access denied"));
  }

  private static boolean isStaff(User user) {
    return user.getRoles().stream().map(Role::getName).anyMatch(STAFF_ROLES::contains);
  }

  private Page<Booking> ownBookings(User viewer, BookingStatus status, Pageable pageable) {
    return status == null
        ? bookings.findByOwnerUsername(viewer.getUsername(), pageable)
        : bookings.findByOwnerUsernameAndStatus(viewer.getUsername(), status, pageable);
  }

  private PagedResponse<BookingResponse> toPaged(Page<Booking> page) {
    return new PagedResponse<>(
        page.getContent().stream().map(mapper::toResponse).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
