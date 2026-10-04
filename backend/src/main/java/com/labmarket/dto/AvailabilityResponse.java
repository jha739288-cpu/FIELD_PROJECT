package com.labmarket.dto;

import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import java.time.Instant;
import java.util.List;

/**
 * Availability of one equipment item over {@code [from, to)}.
 * {@code freePeriods} is empty when the item is not bookable; {@code maintenance}
 * explains why (status or maintenance state), with a human-readable {@code message}.
 */
public record AvailabilityResponse(
    Long equipmentId,
    String equipmentCode,
    String equipmentName,
    EquipmentStatus currentStatus,
    MaintenanceStatus maintenanceStatus,
    boolean bookable,
    Instant from,
    Instant to,
    List<BookedPeriodResponse> bookedPeriods,
    List<TimeSlotResponse> pendingPeriods,
    List<TimeSlotResponse> freePeriods,
    boolean maintenance,
    String message) {}
