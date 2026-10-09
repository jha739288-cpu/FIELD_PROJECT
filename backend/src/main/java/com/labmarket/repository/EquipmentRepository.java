package com.labmarket.repository;

import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Equipment}. Null filter params mean "no filter". */
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

  Optional<Equipment> findByEquipmentCode(String equipmentCode);

  boolean existsByEquipmentCode(String equipmentCode);

  long countByCurrentStatus(EquipmentStatus status);

  long countByMaintenanceStatus(MaintenanceStatus status);

  /**
   * Loads the row with a pessimistic write lock (SELECT ... FOR UPDATE).
   * Booking creation/confirmation locks the equipment first so two concurrent
   * requests for the same item cannot both pass the overlap check.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT e FROM Equipment e WHERE e.id = :id")
  Optional<Equipment> findWithLockById(@Param("id") Long id);

  @Query(
      """
      SELECT e FROM Equipment e
      WHERE (:status IS NULL OR e.currentStatus = :status)
        AND (:category IS NULL OR e.category = :category)
        AND (:laboratory IS NULL OR e.laboratory = :laboratory)
        AND (:pattern IS NULL
             OR LOWER(e.name) LIKE :pattern ESCAPE '|'
             OR LOWER(e.equipmentCode) LIKE :pattern ESCAPE '|'
             OR LOWER(e.description) LIKE :pattern ESCAPE '|'
             OR LOWER(e.manufacturer) LIKE :pattern ESCAPE '|'
             OR LOWER(e.model) LIKE :pattern ESCAPE '|'
             OR LOWER(e.category) LIKE :pattern ESCAPE '|'
             OR LOWER(e.laboratory) LIKE :pattern ESCAPE '|')
      """)
  Page<Equipment> search(
      @Param("status") EquipmentStatus status,
      @Param("category") String category,
      @Param("laboratory") String laboratory,
      @Param("pattern") String pattern,
      Pageable pageable);

  /** Items listed by one vendor (their marketplace inventory). */
  Page<Equipment> findByCreatedById(Long ownerId, Pageable pageable);

  long countByCreatedById(Long ownerId);
}
