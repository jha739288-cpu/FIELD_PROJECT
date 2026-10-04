package com.labmarket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One raw device report. Append-only: ingest validates and flags, never rewrites.
 * Sensor data is untrusted input — see {@code SensorService}.
 */
@Entity
@Table(name = "SENSOR_EVENTS")
public class SensorEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "equipment_id", foreignKey = @ForeignKey(name = "fk_sensor_equipment"))
  private Equipment equipment;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SensorStatus status;

  @Column(name = "current_value", precision = 10, scale = 3)
  private BigDecimal currentValue;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(nullable = false)
  private boolean stale = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public SensorEvent() {}

  @PrePersist
  void onCreate() {
    createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Equipment getEquipment() {
    return equipment;
  }

  public void setEquipment(Equipment equipment) {
    this.equipment = equipment;
  }

  public SensorStatus getStatus() {
    return status;
  }

  public void setStatus(SensorStatus status) {
    this.status = status;
  }

  public BigDecimal getCurrentValue() {
    return currentValue;
  }

  public void setCurrentValue(BigDecimal currentValue) {
    this.currentValue = currentValue;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public void setReceivedAt(Instant receivedAt) {
    this.receivedAt = receivedAt;
  }

  public boolean isStale() {
    return stale;
  }

  public void setStale(boolean stale) {
    this.stale = stale;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
