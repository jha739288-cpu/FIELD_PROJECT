package com.labmarket.dto;

import com.labmarket.entity.EquipmentCondition;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

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
    @Size(max = 500, message = "image URL must be at most 500 characters") String imageUrl,
    @Size(max = 2000, message = "specifications must be at most 2000 characters") String specifications,
    @DecimalMin(value = "0.0", message = "price per hour must be >= 0") BigDecimal pricePerHour,
    @Min(value = 1, message = "quantity must be at least 1") Integer quantity,
    @Size(max = 1000, message = "usage instructions must be at most 1000 characters")
        String usageInstructions,
    @Size(max = 1000, message = "safety info must be at most 1000 characters") String safetyInfo,
    @NotNull(message = "condition is required") EquipmentCondition condition,
    @NotNull(message = "current status is required") EquipmentStatus currentStatus,
    @NotNull(message = "maintenance status is required") MaintenanceStatus maintenanceStatus) {}
