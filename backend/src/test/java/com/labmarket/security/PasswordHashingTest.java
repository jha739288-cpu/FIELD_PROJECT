package com.labmarket.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Pure unit tests: BCrypt hashing behaviour without any Spring context. */
class PasswordHashingTest {

  private final PasswordEncoder encoder = new BCryptPasswordEncoder();

  @Test
  void encodedPasswordMatchesRawPassword() {
    String hash = encoder.encode("correct-horse-8");

    assertTrue(encoder.matches("correct-horse-8", hash));
  }

  @Test
  void wrongPasswordDoesNotMatch() {
    String hash = encoder.encode("correct-horse-8");

    assertFalse(encoder.matches("wrong-password-1", hash));
  }

  @Test
  void twoEncodingsOfSamePasswordDiffer() {
    // BCrypt salts internally: identical passwords must never produce identical hashes.
    String first = encoder.encode("same-password-8");
    String second = encoder.encode("same-password-8");

    assertFalse(first.equals(second));
    assertTrue(encoder.matches("same-password-8", first));
    assertTrue(encoder.matches("same-password-8", second));
  }
}
