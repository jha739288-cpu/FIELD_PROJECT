package com.labmarket.service;

import com.labmarket.entity.AuditEvent;
import com.labmarket.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit trail.
 *
 * <p>Every write uses an independent transaction: a failed check-in still records
 * its {@code QR_VALIDATION_FAILED} event even though the calling transaction rolls back.
 */
@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  public static final String QR_GENERATED = "QR_GENERATED";
  public static final String CHECKED_IN = "CHECKED_IN";
  public static final String CHECKED_OUT = "CHECKED_OUT";
  public static final String QR_VALIDATION_FAILED = "QR_VALIDATION_FAILED";
  public static final String USAGE_STARTED = "USAGE_STARTED";
  public static final String USAGE_ENDED = "USAGE_ENDED";
  public static final String SENSOR_EVENT = "SENSOR_EVENT";
  public static final String SENSOR_KEY_PROVISIONED = "SENSOR_KEY_PROVISIONED";
  public static final String OVERDUE_DETECTED = "OVERDUE_DETECTED";
  public static final String ALERT_RESOLVED = "ALERT_RESOLVED";
  public static final String BOOKING_CONFLICT = "BOOKING_CONFLICT";

  private final AuditEventRepository events;

  public AuditService(AuditEventRepository events) {
    this.events = events;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void log(String eventType, String actorUsername, String entityType, Long entityId, String detail) {
    AuditEvent event = new AuditEvent();
    event.setEventType(eventType);
    event.setActorUsername(actorUsername);
    event.setEntityType(entityType);
    event.setEntityId(entityId);
    event.setDetail(detail);
    events.save(event);
    log.info("AUDIT {} by '{}' on {}#{}: {}", eventType, actorUsername, entityType, entityId, detail);
  }
}
