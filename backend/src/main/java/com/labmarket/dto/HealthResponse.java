package com.labmarket.dto;

import java.time.Instant;

/** Public health payload. Never exposes internals — status string plus UTC timestamp. */
public record HealthResponse(String status, String service, Instant timestamp) {}
