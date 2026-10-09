package com.labmarket.dto;

import com.labmarket.entity.EquipmentCondition;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Public equipment view. Carries no persistence internals. */
public record EquipmentResponse(
    Long id,
    String equipmentCode,
    String name,
    String category,
    String description,
    String manufacturer,
    String model,
    String laboratory,
    String imageUrl,
    String specifications,
    BigDecimal pricePerHour,
    Integer quantity,
    String usageInstructions,
    String safetyInfo,
    EquipmentCondition condition,
    EquipmentStatus currentStatus,
    MaintenanceStatus maintenanceStatus,
    String createdByUsername,
    Instant createdAt,
    Instant updatedAt) {}
