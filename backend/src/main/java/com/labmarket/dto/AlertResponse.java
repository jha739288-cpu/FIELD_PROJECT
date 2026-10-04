package com.labmarket.dto;

import com.labmarket.entity.AlertStatus;
import com.labmarket.entity.AlertType;
import java.time.Instant;

/** Public alert view. */
public record AlertResponse(
    Long id,
    AlertType type,
    AlertStatus status,
    Long bookingId,
    Long equipmentId,
    String equipmentCode,
    String message,
    String resolutionNote,
    String resolvedByUsername,
    Instant createdAt,
    Instant resolvedAt) {}
