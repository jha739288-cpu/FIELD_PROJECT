package com.labmarket.dto;

import com.labmarket.entity.BookingStatus;
import java.time.Instant;

/**
 * One calendar entry. {@code purpose} and {@code username} are only populated
 * for the owner and for staff/admin — other students see the time and status only.
 */
public record BookingSlotResponse(
    Long id, Instant startTime, Instant endTime, BookingStatus status, String purpose, String username) {}
