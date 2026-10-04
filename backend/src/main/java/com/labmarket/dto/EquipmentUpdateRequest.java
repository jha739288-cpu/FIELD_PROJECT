package com.labmarket.dto;

import com.labmarket.entity.EquipmentCondition;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Full-replacement update payload (PUT). The equipment code is immutable
 * and therefore absent — it can never be changed after creation.
 */
public record EquipmentUpdateRequest(
    @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,
    @NotBlank(message = "category is required")
        @Size(max = 80, message = "category must be at most 80 characters")
        String category,
    @Size(max = 1000, message = "description must be at most 1000 characters") String description,
    @Size(max = 100, message = "manufacturer must be at most 100 characters") String manufacturer,
    @Size(max = 100, message = "model must be at most 100 characters") String model,
    @Size(max = 100, message = "laboratory must be at most 100 characters") String laboratory,
    @NotNull(message = "condition is required") EquipmentCondition condition,
    @NotNull(message = "current status is required") EquipmentStatus currentStatus,
    @NotNull(message = "maintenance status is required") MaintenanceStatus maintenanceStatus) {}
