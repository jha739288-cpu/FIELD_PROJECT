package com.labmarket.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * JWT issue/parse/validate. Secret comes from {@code app.jwt.*}
 * (env {@code JWT_SECRET}) — never from source code.
 */
@Service
public class JwtService {

  private final JwtProperties properties;
  private final SecretKey key;

  public JwtService(JwtProperties properties) {
    this.properties = properties;
    byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
    if (secret.length < 32) {
      throw new IllegalStateException("app.jwt.secret must be at least 256 bits (32 bytes)");
    }
    this.key = Keys.hmacShaKeyFor(secret);
  }

  public String generateToken(String username, Collection<String> roles) {
    Date now = new Date();
    return Jwts.builder()
        .subject(username)
        .claim("roles", List.copyOf(roles))
        .issuedAt(now)
        .expiration(new Date(now.getTime() + properties.expirationMs()))
        .signWith(key)
        .compact();
  }

  public String extractUsername(String token) {
    return parse(token).getSubject();
  }

  public List<String> extractRoles(String token) {
    Object roles = parse(token).get("roles");
    if (roles instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of();
  }

  /** Never throws — invalid tokens simply fail validation (filter then leaves the request anonymous). */
  public boolean isValid(String token) {
    try {
      parse(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  private Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }
}
