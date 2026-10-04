package com.labmarket.repository;

import com.labmarket.entity.Alert;
import com.labmarket.entity.AlertStatus;
import com.labmarket.entity.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Alert}. */
public interface AlertRepository extends JpaRepository<Alert, Long> {

  boolean existsByBookingIdAndTypeAndStatus(Long bookingId, AlertType type, AlertStatus status);

  long countByStatus(AlertStatus status);

  long countByTypeAndStatus(AlertType type, AlertStatus status);

  Page<Alert> findByStatus(AlertStatus status, Pageable pageable);

  Page<Alert> findByTypeAndStatus(AlertType type, AlertStatus status, Pageable pageable);
}
