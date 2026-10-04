package com.labmarket.dto;

import java.time.Instant;

/** Point-in-time equipment counts plus trailing-window activity. All from live queries. */
public record SummaryResponse(
    EquipmentSummary equipment,
    BookingSummary bookings,
    UsageSummary usage,
    SensorSummary sensors,
    AlertSummary alerts,
    double utilizationPercent,
    long conflictAttempts,
    Instant windowFrom,
    Instant windowTo,
    Instant computedAt) {

  public record EquipmentSummary(
      long total, long available, long reserved, long inUse, long overdue, long maintenance,
      long sensorOffline) {}

  public record BookingSummary(
      long total, long pending, long confirmed, long checkedIn, long completed, long cancelled,
      long overdue, long rejected) {}

  public record UsageSummary(long sessionsStarted, long sessionsCompleted, long totalSeconds, double totalHours) {}

  public record SensorSummary(
      long events, long staleEvents, long faults, long idle, long inUse, long offline,
      Double reliability) {}

  public record AlertSummary(long open, long resolved) {}
}
