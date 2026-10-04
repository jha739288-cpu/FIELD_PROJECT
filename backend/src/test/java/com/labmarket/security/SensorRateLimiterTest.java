package com.labmarket.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.labmarket.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the fixed-window sensor rate limiter (mutable clock, no Spring). */
class SensorRateLimiterTest {

  @Test
  void burstWithinLimitIsAllowed() {
    SensorRateLimiter limiter = limiter(3, 60, Instant.parse("2026-01-01T00:00:00Z"));

    assertDoesNotThrow(
        () -> {
          limiter.check("sensor:OSC-001");
          limiter.check("sensor:OSC-001");
          limiter.check("sensor:OSC-001");
        });
  }

  @Test
  void overLimitIsRejectedWith429() {
    SensorRateLimiter limiter = limiter(2, 60, Instant.parse("2026-01-01T00:00:00Z"));
    limiter.check("sensor:OSC-001");
    limiter.check("sensor:OSC-001");

    assertThrows(TooManyRequestsException.class, () -> limiter.check("sensor:OSC-001"));
  }

  @Test
  void bucketsAreIndependent() {
    SensorRateLimiter limiter = limiter(1, 60, Instant.parse("2026-01-01T00:00:00Z"));
    limiter.check("sensor:OSC-001");

    assertDoesNotThrow(() -> limiter.check("sensor:OSC-002"));
    assertThrows(TooManyRequestsException.class, () -> limiter.check("sensor:OSC-001"));
  }

  @Test
  void windowResetRestoresQuota() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
    Clock clock = new Clock() {
      @Override
      public ZoneOffset getZone() {
        return ZoneOffset.UTC;
      }

      @Override
      public Clock withZone(java.time.ZoneId zone) {
        return this;
      }

      @Override
      public Instant instant() {
        return now.get();
      }
    };
    SensorRateLimiter limiter = new SensorRateLimiter(1, 60, clock);
    limiter.check("sensor:OSC-001");
    assertThrows(TooManyRequestsException.class, () -> limiter.check("sensor:OSC-001"));

    now.set(now.get().plusSeconds(61));
    assertDoesNotThrow(() -> limiter.check("sensor:OSC-001"));
  }

  private static SensorRateLimiter limiter(int max, long windowSeconds, Instant now) {
    return new SensorRateLimiter(max, windowSeconds, Clock.fixed(now, ZoneOffset.UTC));
  }
}
