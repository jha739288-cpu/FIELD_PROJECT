package com.labmarket.dto;

import java.time.Instant;

/** Public usage-session view. {@code endedAt}/{@code durationSeconds} are null while active. */
public record UsageSessionResponse(
    Long id,
    Long bookingId,
    Long equipmentId,
    String username,
    Instant startedAt,
    Instant endedAt,
    Long durationSeconds) {}
