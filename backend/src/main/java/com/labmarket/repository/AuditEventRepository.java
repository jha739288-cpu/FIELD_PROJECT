package com.labmarket.repository;

import com.labmarket.entity.AuditEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link AuditEvent}. Append-only: no update/delete methods are offered. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

  List<AuditEvent> findByEntityTypeAndEntityIdOrderById(String entityType, Long entityId);

  long countByEventTypeAndCreatedAtBetween(String eventType, Instant from, Instant to);
}
