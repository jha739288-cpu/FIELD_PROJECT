package com.labmarket.repository;

import com.labmarket.entity.SensorEvent;
import com.labmarket.entity.SensorStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link SensorEvent}. Append-only: no update/delete methods are offered. */
public interface SensorEventRepository extends JpaRepository<SensorEvent, Long> {

  List<SensorEvent> findByEquipmentIdOrderByOccurredAtDesc(Long equipmentId);

  Page<SensorEvent> findByEquipmentEquipmentCode(String equipmentCode, Pageable pageable);

  long countByCreatedAtBetween(Instant from, Instant to);

  long countByStaleTrueAndCreatedAtBetween(Instant from, Instant to);

  long countByStaleTrueAndOccurredAtBetween(Instant from, Instant to);

  long countByStatusAndCreatedAtBetween(SensorStatus status, Instant from, Instant to);

  /** Status mix in the window (occurred time — what the devices reported, when). */
  @Query(
      """
      SELECT e.status AS status, COUNT(e) AS total FROM SensorEvent e
      WHERE e.occurredAt >= :from AND e.occurredAt < :to
      GROUP BY e.status
      """)
  List<StatusCount> countByStatusInWindow(@Param("from") Instant from, @Param("to") Instant to);

  /** Event totals per equipment code in the window. */
  @Query(
      """
      SELECT e.equipment.equipmentCode AS code, COUNT(e) AS total FROM SensorEvent e
      WHERE e.occurredAt >= :from AND e.occurredAt < :to
      GROUP BY e.equipment.equipmentCode
      ORDER BY COUNT(e) DESC
      """)
  List<EquipmentCount> countByEquipmentInWindow(
      @Param("from") Instant from, @Param("to") Instant to);

  /** Windowed rows for one item (analytics detail). */
  @Query(
      """
      SELECT e FROM SensorEvent e
      WHERE e.equipment.id = :equipmentId
        AND e.occurredAt >= :from AND e.occurredAt < :to
      """)
  List<SensorEvent> findByEquipmentInWindow(
      @Param("equipmentId") Long equipmentId, @Param("from") Instant from,
      @Param("to") Instant to);

  /** Projection for GROUP BY analytics (H2- and Oracle-safe, no TRUNC needed). */
  interface StatusCount {
    SensorStatus getStatus();

    long getTotal();
  }

  /** Projection for GROUP BY analytics (H2- and Oracle-safe, no TRUNC needed). */
  interface EquipmentCount {
    String getCode();

    long getTotal();
  }
}
