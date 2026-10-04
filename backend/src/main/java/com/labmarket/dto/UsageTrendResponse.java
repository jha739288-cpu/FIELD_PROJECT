package com.labmarket.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily usage trend over {@code [from, to)}. Usage seconds are split across the
 * days they actually occurred on; bookings counted by creation day; sessions
 * ended counted by end day. All derived from stored rows — nothing estimated.
 */
public record UsageTrendResponse(Instant from, Instant to, String granularity, List<TrendPoint> points) {

  public record TrendPoint(
      LocalDate date, long usageSeconds, double usageHours, long sessionsEnded, long bookingsCreated) {}
}
