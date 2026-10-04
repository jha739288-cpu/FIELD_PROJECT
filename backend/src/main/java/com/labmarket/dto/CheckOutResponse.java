package com.labmarket.dto;

/** Check-out outcome: the booking (now COMPLETED) plus its closed session with duration. */
public record CheckOutResponse(BookingResponse booking, UsageSessionResponse session) {}
