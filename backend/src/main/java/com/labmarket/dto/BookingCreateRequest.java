package com.labmarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Booking creation payload. The booking starts as PENDING; staff confirm it. */
public record BookingCreateRequest(
    @NotNull(message = "equipment id is required") Long equipmentId,
    @NotNull(message = "start time is required") Instant startTime,
    @NotNull(message = "end time is required") Instant endTime,
    @NotBlank(message = "purpose is required")
        @Size(max = 500, message = "purpose must be at most 500 characters")
        String purpose) {}
