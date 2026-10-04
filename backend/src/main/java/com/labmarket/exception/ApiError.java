package com.labmarket.exception;

import java.time.Instant;

/** Standard error payload. Entities are never exposed; errors always use this DTO. */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {}
