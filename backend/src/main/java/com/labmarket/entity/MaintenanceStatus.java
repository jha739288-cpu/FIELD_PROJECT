package com.labmarket.entity;

/** Maintenance lifecycle state. Only OPERATIONAL equipment may be AVAILABLE for booking. */
public enum MaintenanceStatus {
  OPERATIONAL,
  DUE,
  IN_MAINTENANCE,
  OUT_OF_SERVICE
}
