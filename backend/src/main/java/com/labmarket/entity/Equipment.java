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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Catalogued laboratory equipment.
 *
 * <p>Domain rules (enforced in {@code EquipmentService}, not just the DB):
 * <ul>
 *   <li>{@code equipmentCode} is an immutable business key.</li>
 *   <li>Only {@code OPERATIONAL} items may carry status {@code AVAILABLE}.</li>
 *   <li>Items that are {@code IN_USE}, {@code RESERVED} or {@code OVERDUE} cannot be deleted.</li>
 * </ul>
 */
@Entity
@Table(
    name = "EQUIPMENT",
    uniqueConstraints = @UniqueConstraint(name = "uq_equipment_code", columnNames = "equipment_code"))
public class Equipment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "equipment_code", nullable = false, length = 50, updatable = false)
  private String equipmentCode;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 80)
  private String category;

  @Column(length = 1000)
  private String description;

  @Column(length = 100)
  private String manufacturer;

  @Column(length = 100)
  private String model;

  @Column(length = 100)
  private String laboratory;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private EquipmentCondition condition = EquipmentCondition.GOOD;

  @Enumerated(EnumType.STRING)
  @Column(name = "current_status", nullable = false, length = 30)
  private EquipmentStatus currentStatus = EquipmentStatus.AVAILABLE;

  @Enumerated(EnumType.STRING)
  @Column(name = "maintenance_status", nullable = false, length = 30)
  private MaintenanceStatus maintenanceStatus = MaintenanceStatus.OPERATIONAL;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by", foreignKey = @ForeignKey(name = "fk_equipment_created_by"))
  private User createdBy;

  /** SHA-256 of the device API key (X-Sensor-Key). NULL = no key provisioned. Never exposed. */
  @Column(name = "sensor_key_hash", length = 64)
  private String sensorKeyHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Equipment() {}

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

  public String getEquipmentCode() {
    return equipmentCode;
  }

  public void setEquipmentCode(String equipmentCode) {
    this.equipmentCode = equipmentCode;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getManufacturer() {
    return manufacturer;
  }

  public void setManufacturer(String manufacturer) {
    this.manufacturer = manufacturer;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getLaboratory() {
    return laboratory;
  }

  public void setLaboratory(String laboratory) {
    this.laboratory = laboratory;
  }

  public EquipmentCondition getCondition() {
    return condition;
  }

  public void setCondition(EquipmentCondition condition) {
    this.condition = condition;
  }

  public EquipmentStatus getCurrentStatus() {
    return currentStatus;
  }

  public void setCurrentStatus(EquipmentStatus currentStatus) {
    this.currentStatus = currentStatus;
  }

  public MaintenanceStatus getMaintenanceStatus() {
    return maintenanceStatus;
  }

  public void setMaintenanceStatus(MaintenanceStatus maintenanceStatus) {
    this.maintenanceStatus = maintenanceStatus;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  public String getSensorKeyHash() {
    return sensorKeyHash;
  }

  public void setSensorKeyHash(String sensorKeyHash) {
    this.sensorKeyHash = sensorKeyHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
