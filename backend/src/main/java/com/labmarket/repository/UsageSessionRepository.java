package com.labmarket.repository;

import com.labmarket.entity.UsageSession;
import com.labmarket.entity.UsageSource;
import com.labmarket.entity.UsageStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link UsageSession}. Null filter params mean "no filter". */
public interface UsageSessionRepository extends JpaRepository<UsageSession, Long> {

  Optional<UsageSession> findByBookingId(Long bookingId);

  /** Session history for one item (prediction input). */
  List<UsageSession> findByEquipmentIdAndStartedAtBetween(Long equipmentId, Instant from, Instant to);

  long countByStartedAtBetween(Instant from, Instant to);

  long countByStatusAndEndedAtBetween(UsageStatus status, Instant from, Instant to);

  @Query(
      """
      SELECT COALESCE(SUM(s.durationSeconds), 0) FROM UsageSession s
      WHERE s.status = com.labmarket.entity.UsageStatus.COMPLETED
        AND s.endedAt >= :from AND s.endedAt < :to
      """)
  long sumCompletedDuration(@Param("from") Instant from, @Param("to") Instant to);

  /**
   * Sessions touching {@code [from, to)} (open ones included) — the raw material
   * for utilization math, which clips and splits in Java for exactness.
   */
  @Query(
      """
      SELECT s FROM UsageSession s
      WHERE s.startedAt < :to AND (s.endedAt IS NULL OR s.endedAt > :from)
      """)
  List<UsageSession> findOverlapping(@Param("from") Instant from, @Param("to") Instant to);

  /**
   * One user's sessions touching {@code [from, to)} — personal analytics input.
   * Covered by {@code ix_usage_user}.
   */
  @Query(
      """
      SELECT s FROM UsageSession s
      WHERE s.user.id = :userId
        AND s.startedAt < :to AND (s.endedAt IS NULL OR s.endedAt > :from)
      """)
  List<UsageSession> findOverlappingForUser(
      @Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      """
      SELECT s FROM UsageSession s
      WHERE s.equipment.id = :equipmentId
        AND s.startedAt < :to AND (s.endedAt IS NULL OR s.endedAt > :from)
      """)
  List<UsageSession> findOverlappingForEquipment(
      @Param("equipmentId") Long equipmentId, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      """
      SELECT s FROM UsageSession s
      JOIN s.equipment e
      WHERE (:equipmentId IS NULL OR e.id = :equipmentId)
        AND (:userId IS NULL OR s.user.id = :userId)
        AND (:status IS NULL OR s.status = :status)
        AND (:source IS NULL OR s.source = :source)
        AND (:laboratory IS NULL OR e.laboratory = :laboratory)
        AND (:from IS NULL OR s.startedAt >= :from)
        AND (:to IS NULL OR s.startedAt < :to)
      """)
  Page<UsageSession> search(
      @Param("equipmentId") Long equipmentId,
      @Param("userId") Long userId,
      @Param("status") UsageStatus status,
      @Param("source") UsageSource source,
      @Param("laboratory") String laboratory,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}
