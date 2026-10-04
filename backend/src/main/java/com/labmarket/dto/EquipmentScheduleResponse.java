package com.labmarket.dto;

import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import java.time.Instant;
import java.util.List;

/** One equipment lane of the calendar: identity, live status and its bookings in the window. */
public record EquipmentScheduleResponse(
    Long equipmentId,
    String equipmentCode,
    String equipmentName,
    EquipmentStatus currentStatus,
    MaintenanceStatus maintenanceStatus,
    Instant from,
    Instant to,
    List<BookingSlotResponse> slots) {}
