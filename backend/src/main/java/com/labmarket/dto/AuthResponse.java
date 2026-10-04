package com.labmarket.dto;

import java.util.List;

/** Issued on register/login. Contains no password material of any kind. */
public record AuthResponse(String token, String tokenType, String username, List<String> roles, long expiresInMs) {}
