package com.labmarket.dto;

import java.util.List;
import java.util.Map;

/** A vendor's own marketplace numbers: inventory, demand on it, and utilization. */
public record VendorDashboardResponse(
    String username,
    long totalEquipment,
    Map<String, Long> equipmentByStatus,
    long totalBookings,
    Map<String, Long> bookingsByStatus,
    long completedSessions,
    long usageSeconds,
    double usageHours,
    List<BookingBrief> recentBookings) {

  public record BookingBrief(
      Long id, String equipmentCode, String username, String status, String startTime) {}
}
