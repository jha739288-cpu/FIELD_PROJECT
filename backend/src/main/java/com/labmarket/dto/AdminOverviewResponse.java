package com.labmarket.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Platform overview for admins: real counts plus recent rows. */
public record AdminOverviewResponse(
    long totalUsers,
    Map<String, Long> usersByRole,
    Map<String, Long> equipmentByStatus,
    Map<String, Long> bookingsByStatus,
    long openAlerts,
    List<UserBrief> recentUsers,
    List<BookingBrief> recentBookings,
    List<EquipmentBrief> recentEquipment) {

  public record UserBrief(Long id, String username, String email, List<String> roles, Instant createdAt) {}

  public record BookingBrief(
      Long id, String equipmentCode, String username, String status, Instant startTime) {}

  public record EquipmentBrief(Long id, String equipmentCode, String name, String status) {}
}
