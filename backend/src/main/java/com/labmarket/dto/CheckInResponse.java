package com.labmarket.dto;

/** Check-in outcome: the booking (now CHECKED_IN) plus its opened usage session. */
public record CheckInResponse(BookingResponse booking, UsageSessionResponse session) {}
