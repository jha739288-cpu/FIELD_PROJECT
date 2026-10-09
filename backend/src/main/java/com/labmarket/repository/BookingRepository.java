package com.labmarket.repository;

import com.labmarket.entity.Booking;
import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.Equipment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Booking}. Overlap logic lives in {@code BookingService}. */
public interface BookingRepository extends JpaRepository<Booking, Long> {

  /**
   * True when an active booking for the same equipment overlaps
   * {@code [start, end)}. Back-to-back ranges (existing.end == start or
   * existing.start == end) do NOT overlap.
   */
  @Query(
      """
      SELECT COUNT(b) > 0 FROM Booking b
      WHERE b.equipment = :equipment
        AND b.status IN :statuses
        AND b.startTime < :end
        AND b.endTime > :start
      """)
  boolean existsOverlap(
      @Param("equipment") Equipment equipment,
      @Param("statuses") Collection<BookingStatus> statuses,
      @Param("start") Instant start,
      @Param("end") Instant end);

  Page<Booking> findByOwnerUsername(String username, Pageable pageable);

  Page<Booking> findByOwnerUsernameAndStatus(String username, BookingStatus status, Pageable pageable);

  /** Any booking ever made for the item (history protection on delete). */
  boolean existsByEquipmentId(Long equipmentId);

  long countByStatus(BookingStatus status);

  long countByOwnerUsername(String username);

  long countByOwnerUsernameAndStatus(String username, BookingStatus status);

  long countByEquipmentIdAndStatus(Long equipmentId, BookingStatus status);

  List<Booking> findByCreatedAtBetween(Instant from, Instant to);

  /** Realized demand history for one item (prediction input). */
  List<Booking> findByEquipmentIdAndStatusInAndStartTimeBetween(
      Long equipmentId, Collection<BookingStatus> statuses, Instant from, Instant to);

  /** Live bookings whose end has passed — the overdue candidates. */
  List<Booking> findByStatusInAndEndTimeBefore(
      Collection<BookingStatus> statuses, Instant endTime);

  /**
   * Windowed booking rows for analytics grouping (creation time). Grouping by day
   * happens in Java (UTC) so the query stays portable across Oracle and H2
   * without dialect-specific date truncation.
   */
  @Query(
      """
      SELECT b.createdAt AS created, b.status AS status,
             b.equipment.id AS equipmentId, b.equipment.equipmentCode AS code
      FROM Booking b
      WHERE b.createdAt >= :from AND b.createdAt < :to
      """)
  List<BookingRow> findRowsInWindow(@Param("from") Instant from, @Param("to") Instant to);

  /** Projection for analytics grouping (no TRUNC needed). */
  interface BookingRow {
    Instant getCreated();

    BookingStatus getStatus();

    Long getEquipmentId();

    String getCode();
  }

  /** History slice for prediction: bookings of one item overlapping [from, to). */
  List<Booking> findByEquipmentIdAndStatusInAndStartTimeBeforeAndEndTimeAfter(
      Long equipmentId, Collection<BookingStatus> statuses, Instant to, Instant from);

  /** True while the equipment has a live checked-in booking (sensor must not override it). */
  boolean existsByEquipmentIdAndStatus(Long equipmentId, BookingStatus status);

  /**
   * Bookings of one equipment item overlapping {@code [from, to)}, ordered by start.
   * Used by the schedule/availability/calendar views (read-only, no locks).
   */
  @Query(
      """
      SELECT b FROM Booking b
      WHERE b.equipment.id = :equipmentId
        AND b.status IN :statuses
        AND b.startTime < :to
        AND b.endTime > :from
      ORDER BY b.startTime
      """)
  List<Booking> findOverlapping(
      @Param("equipmentId") Long equipmentId,
      @Param("statuses") Collection<BookingStatus> statuses,
      @Param("from") Instant from,
      @Param("to") Instant to);

  /**
   * Bookings of ALL equipment overlapping {@code [from, to)}, ordered by equipment then start.
   * Used by the calendar view (read-only, no locks).
   */
  @Query(
      """
      SELECT b FROM Booking b
      WHERE b.status IN :statuses
        AND b.startTime < :to
        AND b.endTime > :from
      ORDER BY b.equipment.id, b.startTime
      """)
  List<Booking> findAllOverlapping(
      @Param("statuses") Collection<BookingStatus> statuses,
      @Param("from") Instant from,
      @Param("to") Instant to);

  @Query(
      """
      SELECT b FROM Booking b
      WHERE (:equipmentId IS NULL OR b.equipment.id = :equipmentId)
        AND (:status IS NULL OR b.status = :status)
      """)
  Page<Booking> search(
      @Param("equipmentId") Long equipmentId,
      @Param("status") BookingStatus status,
      Pageable pageable);

  /** Bookings on equipment listed by one vendor (ownership-scoped staff view). */
  @Query(
      """
      SELECT b FROM Booking b
      WHERE b.equipment.createdBy.id = :ownerId
        AND (:equipmentId IS NULL OR b.equipment.id = :equipmentId)
        AND (:status IS NULL OR b.status = :status)
      """)
  Page<Booking> searchForOwner(
      @Param("ownerId") Long ownerId,
      @Param("equipmentId") Long equipmentId,
      @Param("status") BookingStatus status,
      Pageable pageable);

  /** Any booking ever made by one user (admin user-management counts). */
  long countByOwner_Id(Long ownerId);

  /** Bookings on equipment listed by one vendor. */
  long countByEquipmentCreatedById(Long ownerId);

  @Query("SELECT b FROM Booking b WHERE b.equipment.createdBy.id = :ownerId")
  Page<Booking> findByEquipmentOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

  @Query(
      "SELECT b.status, COUNT(b) FROM Booking b WHERE b.equipment.createdBy.id = :ownerId GROUP BY b.status")
  List<Object[]> countByStatusForOwner(@Param("ownerId") Long ownerId);
}
