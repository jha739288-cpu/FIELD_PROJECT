package com.labmarket.mapper;

import com.labmarket.dto.UsageLogResponse;
import com.labmarket.entity.UsageSession;
import org.springframework.stereotype.Component;

/** Entity → DTO conversions for usage logs. */
@Component
public class UsageLogMapper {

  public UsageLogResponse toResponse(UsageSession s) {
    return new UsageLogResponse(
        s.getId(),
        s.getBooking() == null ? null : s.getBooking().getId(),
        s.getEquipment().getId(),
        s.getEquipment().getEquipmentCode(),
        s.getEquipment().getName(),
        s.getUser().getId(),
        s.getUser().getUsername(),
        s.getStartedAt(),
        s.getEndedAt(),
        s.getDurationSeconds(),
        s.getSource(),
        s.getStatus(),
        s.getNote(),
        s.getCreatedAt());
  }
}
