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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Operational alert. At most one OPEN alert exists per booking — enforced by
 * {@code OverdueService} (Oracle has no partial unique indexes).
 */
@Entity
@Table(name = "ALERTS")
public class Alert {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private AlertType type;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "booking_id", foreignKey = @ForeignKey(name = "fk_alerts_booking"))
  private Booking booking;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "equipment_id", foreignKey = @ForeignKey(name = "fk_alerts_equipment"))
  private Equipment equipment;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AlertStatus status = AlertStatus.OPEN;

  @Column(nullable = false, length = 500)
  private String message;

  @Column(name = "resolution_note", length = 500)
  private String resolutionNote;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "resolved_by", foreignKey = @ForeignKey(name = "fk_alerts_resolved_by"))
  private User resolvedBy;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Alert() {}

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public AlertType getType() {
    return type;
  }

  public void setType(AlertType type) {
    this.type = type;
  }

  public Booking getBooking() {
    return booking;
  }

  public void setBooking(Booking booking) {
    this.booking = booking;
  }

  public Equipment getEquipment() {
    return equipment;
  }

  public void setEquipment(Equipment equipment) {
    this.equipment = equipment;
  }

  public AlertStatus getStatus() {
    return status;
  }

  public void setStatus(AlertStatus status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getResolutionNote() {
    return resolutionNote;
  }

  public void setResolutionNote(String resolutionNote) {
    this.resolutionNote = resolutionNote;
  }

  public User getResolvedBy() {
    return resolvedBy;
  }

  public void setResolvedBy(User resolvedBy) {
    this.resolvedBy = resolvedBy;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
