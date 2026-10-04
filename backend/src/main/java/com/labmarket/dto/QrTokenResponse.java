package com.labmarket.dto;

import java.time.Instant;

/**
 * Issued once at generation. The raw token is rendered as a QR code and never
 * stored — only its hash lives in the database.
 */
public record QrTokenResponse(String qrToken, Long bookingId, Instant expiresAt) {}
