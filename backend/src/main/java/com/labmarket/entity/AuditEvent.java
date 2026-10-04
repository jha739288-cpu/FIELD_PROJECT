package com.labmarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Append-only audit record. Written via {@code AuditService} in its own
 * transaction so failed operations still leave a trail.
 */
@Entity
@Table(name = "AUDIT_EVENTS")
public class AuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_type", nullable = false, length = 40)
  private String eventType;

  @Column(name = "actor_username", length = 50)
  private String actorUsername;

  @Column(name = "entity_type", length = 40)
  private String entityType;

  @Column(name = "entity_id")
  private Long entityId;

  @Column(length = 1000)
  private String detail;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public AuditEvent() {}

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getActorUsername() {
    return actorUsername;
  }

  public void setActorUsername(String actorUsername) {
    this.actorUsername = actorUsername;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public Long getEntityId() {
    return entityId;
  }

  public void setEntityId(Long entityId) {
    this.entityId = entityId;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
