package com.labmarket.dto;

import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.SensorStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Stored event plus its effect. {@code appliedEquipmentStatus} is the equipment
 * status after ingest (unchanged when the mapping left it alone).
 */
public record SensorEventResponse(
    Long id,
    String equipmentCode,
    SensorStatus status,
    BigDecimal currentValue,
    Instant occurredAt,
    Instant receivedAt,
    boolean stale,
    EquipmentStatus appliedEquipmentStatus) {}
