package com.labmarket.dto;

import com.labmarket.entity.UsageSource;
import com.labmarket.entity.UsageStatus;
import java.time.Instant;

/**
 * Usage-log view. {@code bookingId} is null for MANUAL entries;
 * {@code endedAt}/{@code durationSeconds} are null while ACTIVE.
 */
public record UsageLogResponse(
    Long id,
    Long bookingId,
    Long equipmentId,
    String equipmentCode,
    String equipmentName,
    Long userId,
    String username,
    Instant startedAt,
    Instant endedAt,
    Long durationSeconds,
    UsageSource source,
    UsageStatus status,
    String note,
    Instant createdAt) {}
