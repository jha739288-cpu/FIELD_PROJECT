package com.labmarket.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit tests: JWT issue/parse/validate without any Spring context. */
class JwtServiceTest {

  private static final String SECRET = "unit-test-secret-min-32-chars-long-xxxx";

  private final JwtService jwt = new JwtService(new JwtProperties(SECRET, 3_600_000));

  @Test
  void generatedTokenValidatesAndCarriesSubjectAndRoles() {
    String token = jwt.generateToken("alice", List.of("USER"));

    assertTrue(jwt.isValid(token));
    assertEquals("alice", jwt.extractUsername(token));
    assertEquals(List.of("USER"), jwt.extractRoles(token));
  }

  @Test
  void tamperedTokenFailsValidation() {
    String token = jwt.generateToken("alice", List.of("USER"));
    String tampered = token.substring(0, token.length() - 2) + "xx";

    assertFalse(jwt.isValid(tampered));
  }

  @Test
  void tokenSignedWithAnotherSecretFailsValidation() {
    JwtService other = new JwtService(new JwtProperties("a-different-32-char-secret-zzzzzz", 3_600_000));
    String foreign = other.generateToken("alice", List.of("USER"));

    assertFalse(jwt.isValid(foreign));
  }

  @Test
  void expiredTokenFailsValidation() {
    JwtService expired =
        new JwtService(new JwtProperties(SECRET, -1_000)); // already expired at issue time
    String token = expired.generateToken("alice", List.of("USER"));

    assertFalse(jwt.isValid(token));
  }

  @Test
  void shortSecretIsRejectedAtStartup() {
    assertThrows(IllegalStateException.class, () -> new JwtService(new JwtProperties("too-short", 1000)));
  }
}
