package com.labmarket.dto;

import com.labmarket.entity.SensorStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One device report. All fields are validated; timestamps are range-checked
 * in the service (future/ancient/stale). Current is optional (status-only reports).
 */
public record SensorEventRequest(
    @NotBlank(message = "equipment code is required")
        @Size(max = 50, message = "equipment code must be at most 50 characters")
        String equipmentCode,
    @NotNull(message = "status is required") SensorStatus status,
    @DecimalMin(value = "0.0", message = "current must be >= 0")
        @DecimalMax(value = "100.0", message = "current must be <= 100 A")
        BigDecimal currentValue,
    @NotNull(message = "timestamp is required") Instant timestamp) {}
