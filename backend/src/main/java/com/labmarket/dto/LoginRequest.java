package com.labmarket.dto;

import jakarta.validation.constraints.NotBlank;

/** Login payload. Failure always returns 401 without revealing which field was wrong. */
public record LoginRequest(
    @NotBlank(message = "username is required") String username,
    @NotBlank(message = "password is required") String password) {}
