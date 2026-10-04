package com.labmarket.dto;

import com.labmarket.entity.BookingStatus;
import com.labmarket.entity.EquipmentCondition;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** One equipment item: live status, windowed bookings/usage and recent reservations. */
public record EquipmentDashboardResponse(
    Long id,
    String equipmentCode,
    String name,
    String category,
    EquipmentCondition condition,
    EquipmentStatus currentStatus,
    MaintenanceStatus maintenanceStatus,
    Instant from,
    Instant to,
    Map<BookingStatus, Long> bookingsByStatus,
    long sessions,
    long completedSessions,
    long usageSeconds,
    double utilizationPercent,
    List<BookingBriefResponse> recentBookings) {

  /** Slim reservation view: times + status only (no owner data, no purpose). */
  public record BookingBriefResponse(Long id, Instant startTime, Instant endTime, BookingStatus status) {}
}
