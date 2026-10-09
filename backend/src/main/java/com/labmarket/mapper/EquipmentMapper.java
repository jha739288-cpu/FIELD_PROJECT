package com.labmarket.mapper;

import com.labmarket.dto.EquipmentCreateRequest;
import com.labmarket.dto.EquipmentResponse;
import com.labmarket.dto.EquipmentUpdateRequest;
import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentCondition;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Entity ↔ DTO conversions for equipment. */
@Component
public class EquipmentMapper {

  public Equipment toEntity(EquipmentCreateRequest req) {
    Equipment e = new Equipment();
    e.setEquipmentCode(req.equipmentCode());
    apply(e, req.name(), req.category(), req.description(), req.manufacturer(), req.model(),
        req.laboratory(), req.imageUrl(), req.specifications(), req.pricePerHour(), req.quantity(),
        req.usageInstructions(), req.safetyInfo(),
        req.effectiveCondition(), req.effectiveStatus(), req.effectiveMaintenanceStatus());
    return e;
  }

  public void applyUpdate(Equipment e, EquipmentUpdateRequest req) {
    apply(e, req.name(), req.category(), req.description(), req.manufacturer(), req.model(),
        req.laboratory(), req.imageUrl(), req.specifications(), req.pricePerHour(), req.quantity(),
        req.usageInstructions(), req.safetyInfo(),
        req.condition(), req.currentStatus(), req.maintenanceStatus());
  }

  private void apply(
      Equipment e,
      String name,
      String category,
      String description,
      String manufacturer,
      String model,
      String laboratory,
      String imageUrl,
      String specifications,
      BigDecimal pricePerHour,
      Integer quantity,
      String usageInstructions,
      String safetyInfo,
      EquipmentCondition condition,
      EquipmentStatus status,
      MaintenanceStatus maintenance) {
    e.setName(name);
    e.setCategory(category);
    e.setDescription(description);
    e.setManufacturer(manufacturer);
    e.setModel(model);
    e.setLaboratory(laboratory);
    e.setImageUrl(imageUrl);
    e.setSpecifications(specifications);
    e.setPricePerHour(pricePerHour);
    e.setQuantity(quantity == null ? 1 : quantity);
    e.setUsageInstructions(usageInstructions);
    e.setSafetyInfo(safetyInfo);
    e.setCondition(condition);
    e.setCurrentStatus(status);
    e.setMaintenanceStatus(maintenance);
  }

  public EquipmentResponse toResponse(Equipment e) {
    String createdBy = e.getCreatedBy() == null ? null : e.getCreatedBy().getUsername();
    return new EquipmentResponse(
        e.getId(),
        e.getEquipmentCode(),
        e.getName(),
        e.getCategory(),
        e.getDescription(),
        e.getManufacturer(),
        e.getModel(),
        e.getLaboratory(),
        e.getImageUrl(),
        e.getSpecifications(),
        e.getPricePerHour(),
        e.getQuantity(),
        e.getUsageInstructions(),
        e.getSafetyInfo(),
        e.getCondition(),
        e.getCurrentStatus(),
        e.getMaintenanceStatus(),
        createdBy,
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
