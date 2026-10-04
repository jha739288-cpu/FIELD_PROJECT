package com.labmarket.entity;

/** Device-reported state. Mapped to equipment status by {@code SensorService}, never 1:1 blindly. */
public enum SensorStatus {
  IDLE,
  IN_USE,
  OFFLINE,
  FAULT
}
