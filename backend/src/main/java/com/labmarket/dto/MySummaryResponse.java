package com.labmarket.dto;

import com.labmarket.dto.SummaryResponse.BookingSummary;
import com.labmarket.dto.SummaryResponse.UsageSummary;
import java.time.Instant;

/**
 * Personal analytics for one student: own bookings and own usage only.
 * Reuses the summary shapes so the frontend renders them with the same components.
 */
public record MySummaryResponse(
    String username,
    BookingSummary bookings,
    UsageSummary usage,
    Instant windowFrom,
    Instant windowTo,
    Instant computedAt) {}
