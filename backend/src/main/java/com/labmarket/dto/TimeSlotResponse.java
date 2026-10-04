package com.labmarket.dto;

import java.time.Instant;

/** Half-open range {@code [startTime, endTime)} used for free and pending periods. */
public record TimeSlotResponse(Instant startTime, Instant endTime) {}
