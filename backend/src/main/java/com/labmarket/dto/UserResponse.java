package com.labmarket.dto;

import java.util.List;

/** Public user view. Never includes the password hash. */
public record UserResponse(
    Long id, String username, String email, String fullName, boolean enabled, List<String> roles) {}
