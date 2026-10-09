package com.labmarket.dto;

import java.time.Instant;

/** Admin view of a vendor account with marketplace activity. */
public record VendorSummaryResponse(
    Long id,
    String username,
    String email,
    String fullName,
    boolean enabled,
    Instant createdAt,
    long equipmentCount,
    long bookingCount) {}
