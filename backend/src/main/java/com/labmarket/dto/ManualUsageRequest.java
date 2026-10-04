package com.labmarket.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Staff-recorded historical usage (walk-ins, offline use). Always COMPLETED:
 * both bounds are required and the end must not be in the future.
 */
public record ManualUsageRequest(
    @NotNull(message = "equipment id is required") Long equipmentId,
    @NotNull(message = "user id is required") Long userId,
    @NotNull(message = "start time is required") Instant startTime,
    @NotNull(message = "end time is required") Instant endTime,
    @Size(max = 500, message = "note must be at most 500 characters") String note) {}
