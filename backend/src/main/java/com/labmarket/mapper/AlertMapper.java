package com.labmarket.mapper;

import com.labmarket.dto.AlertResponse;
import com.labmarket.entity.Alert;
import org.springframework.stereotype.Component;

/** Entity → DTO conversions for alerts. */
@Component
public class AlertMapper {

  public AlertResponse toResponse(Alert a) {
    return new AlertResponse(
        a.getId(),
        a.getType(),
        a.getStatus(),
        a.getBooking().getId(),
        a.getEquipment().getId(),
        a.getEquipment().getEquipmentCode(),
        a.getMessage(),
        a.getResolutionNote(),
        a.getResolvedBy() == null ? null : a.getResolvedBy().getUsername(),
        a.getCreatedAt(),
        a.getResolvedAt());
  }
}
