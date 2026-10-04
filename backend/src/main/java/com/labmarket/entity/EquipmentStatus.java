package com.labmarket.entity;

/**
 * Live availability of a piece of equipment.
 * Owned at runtime by the booking / QR / sensor modules — the catalog module
 * only sets the initial value and staff corrections.
 */
public enum EquipmentStatus {
  AVAILABLE,
  RESERVED,
  IN_USE,
  OVERDUE,
  MAINTENANCE,
  SENSOR_OFFLINE
}
