package com.labmarket.mapper;

import com.labmarket.dto.BookingResponse;
import com.labmarket.entity.Booking;
import org.springframework.stereotype.Component;

/** Entity → DTO conversions for bookings. */
@Component
public class BookingMapper {

  public BookingResponse toResponse(Booking b) {
    return new BookingResponse(
        b.getId(),
        b.getEquipment().getId(),
        b.getEquipment().getEquipmentCode(),
        b.getEquipment().getName(),
        b.getOwner().getId(),
        b.getOwner().getUsername(),
        b.getStartTime(),
        b.getEndTime(),
        b.getStatus(),
        b.getPurpose(),
        b.getCreatedAt(),
        b.getUpdatedAt());
  }
}
