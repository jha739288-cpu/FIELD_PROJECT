package com.labmarket.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Booking analytics over bookings created in {@code [from, to)}.
 * Day buckets use UTC dates; equipment rows are ordered by count descending.
 */
public record BookingAnalyticsResponse(
    Instant from,
    Instant to,
    long total,
    Map<String, Long> byStatus,
    List<DayCount> byDay,
    List<EquipmentBookingCount> byEquipment,
    long cancelled,
    long overdue) {

  public record DayCount(String date, long count) {}

  public record EquipmentBookingCount(Long equipmentId, String equipmentCode, long count) {}
}
