package com.labmarket.entity;

/**
 * Usage-session lifecycle. COMPLETED rows are historical and immutable —
 * there is deliberately no update/delete path for them.
 */
public enum UsageStatus {
  ACTIVE,
  COMPLETED
}
