package com.labmarket.dto;

import com.labmarket.entity.BookingStatus;
import java.time.Instant;

/** A blocking reservation, clipped to the requested window. */
public record BookedPeriodResponse(Long bookingId, Instant startTime, Instant endTime, BookingStatus status) {}
