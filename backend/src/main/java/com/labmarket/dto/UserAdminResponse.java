package com.labmarket.dto;

import java.time.Instant;
import java.util.List;

/** Admin view of an account. Never includes password material. */
public record UserAdminResponse(
    Long id,
    String username,
    String email,
    String fullName,
    boolean enabled,
    List<String> roles,
    Instant createdAt,
    long bookingCount) {}
