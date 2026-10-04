package com.labmarket.dto;

import jakarta.validation.constraints.Size;

/** Optional staff note recorded when resolving an alert. */
public record ResolveAlertRequest(
    @Size(max = 500, message = "note must be at most 500 characters") String note) {}
