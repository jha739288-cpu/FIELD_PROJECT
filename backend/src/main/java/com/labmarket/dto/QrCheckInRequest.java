package com.labmarket.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Check-in payload. Carries ONLY the scanned token — the server resolves
 * booking, equipment and owner from it and trusts nothing else.
 */
public record QrCheckInRequest(@NotBlank(message = "QR token is required") String qrToken) {}
