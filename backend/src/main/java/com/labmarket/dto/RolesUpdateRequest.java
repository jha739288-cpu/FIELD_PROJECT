package com.labmarket.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Replace an account's roles. Each must be a known role; ADMIN included only deliberately. */
public record RolesUpdateRequest(@NotEmpty(message = "at least one role is required") List<String> roles) {}
