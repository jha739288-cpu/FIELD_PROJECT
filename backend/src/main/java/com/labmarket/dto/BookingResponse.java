package com.labmarket.dto;

import com.labmarket.entity.BookingStatus;
import java.time.Instant;

/** Public booking view. Carries no persistence internals. */
public record BookingResponse(
    Long id,
    Long equipmentId,
    String equipmentCode,
    String equipmentName,
    Long userId,
    String username,
    Instant startTime,
    Instant endTime,
    BookingStatus status,
    String purpose,
    Instant createdAt,
    Instant updatedAt) {}
