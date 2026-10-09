package com.labmarket.dto;

import com.labmarket.entity.EquipmentCondition;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Create payload. Staff/admin only. Status fields are optional and default sensibly. */
public record EquipmentCreateRequest(
    @NotBlank(message = "equipment code is required")
        @Size(max = 50, message = "equipment code must be at most 50 characters")
        @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "equipment code may contain letters, digits and '-'")
        String equipmentCode,
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
    EquipmentCondition condition,
    EquipmentStatus currentStatus,
    MaintenanceStatus maintenanceStatus) {

  /** Cross-field rule: only OPERATIONAL items may be AVAILABLE. Checked in the service. */
  public EquipmentCondition effectiveCondition() {
    return condition == null ? EquipmentCondition.GOOD : condition;
  }

  public EquipmentStatus effectiveStatus() {
    return currentStatus == null ? EquipmentStatus.AVAILABLE : currentStatus;
  }

  public MaintenanceStatus effectiveMaintenanceStatus() {
    return maintenanceStatus == null ? MaintenanceStatus.OPERATIONAL : maintenanceStatus;
  }
}
