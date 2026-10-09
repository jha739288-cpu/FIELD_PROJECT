package com.labmarket.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-registration payload. Creates a USER account by default; VENDOR may be
 * requested at registration. ADMIN is never self-assigned (400) — admins are
 * created via the seed runner or by another ADMIN.
 */
public record RegisterRequest(
    @NotBlank(message = "username is required")
        @Size(min = 3, max = 50, message = "username must be 3-50 characters")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "username may contain letters, digits, '.', '_' or '-'")
        String username,
    @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,
    @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be 8-100 characters")
        String password,
    @Size(max = 100, message = "full name must be at most 100 characters") String fullName,
    @Pattern(regexp = "^(USER|VENDOR)$", message = "role must be USER or VENDOR") String role) {

  /** Requested role, defaulting to USER when omitted. */
  public String effectiveRole() {
    return role == null ? "USER" : role;
  }
}
