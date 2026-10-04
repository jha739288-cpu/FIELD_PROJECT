package com.labmarket.dto;

import java.time.Instant;
import java.util.List;

/** Calendar view: one schedule lane per equipment item for the requested window. */
public record CalendarResponse(Instant from, Instant to, List<EquipmentScheduleResponse> schedules) {}
