package com.labmarket.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings bound from {@code app.jwt.*}. The signing filter and
 * login/refresh endpoints arrive in Module 1 (auth); this record only
 * centralises configuration access so nothing reads {@code @Value} inline.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long expirationMs) {}
