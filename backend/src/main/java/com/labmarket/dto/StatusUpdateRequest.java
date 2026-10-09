package com.labmarket.dto;

import jakarta.validation.constraints.NotNull;

/** Enable/disable an account. */
public record StatusUpdateRequest(@NotNull(message = "enabled is required") Boolean enabled) {}
