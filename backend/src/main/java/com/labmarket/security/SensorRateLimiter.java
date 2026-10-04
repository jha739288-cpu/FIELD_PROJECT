package com.labmarket.security;

import com.labmarket.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limiter for the unauthenticated sensor ingest path.
 * Per device key: {@code maxRequests} per {@code window}. Limits audit-table
 * spam and brute-force key probing from a single key namespace.
 *
 * <p>Dependency-free and in-memory: correct for a single-instance capstone
 * deployment (documented limitation — a clustered deployment would need a
 * shared store). Time comes from an injectable {@link Clock} for tests.
 */
@Component
public class SensorRateLimiter {

  private final int maxRequests;
  private final Duration window;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  public SensorRateLimiter(
      @Value("${app.sensor.rate-limit.max-requests:240}") int maxRequests,
      @Value("${app.sensor.rate-limit.window-seconds:60}") long windowSeconds,
      Clock clock) {
    this.maxRequests = maxRequests;
    this.window = Duration.ofSeconds(windowSeconds);
    this.clock = clock;
  }

  /** Throws {@link TooManyRequestsException} when the key exhausted its window. */
  public void check(String bucket) {
    String key = bucket == null ? "" : bucket;
    Instant now = clock.instant();
    windows.compute(
        key,
        (k, w) -> {
          if (w == null || !now.isBefore(w.windowEnd)) {
            w = new Window(now.plus(window));
          }
          w.count++;
          if (w.count > maxRequests) {
            throw new TooManyRequestsException(
                "Sensor rate limit exceeded (max " + maxRequests + " requests per "
                    + window.toSeconds() + "s)");
          }
          return w;
        });
  }

  private static final class Window {
    final Instant windowEnd;
    int count;

    Window(Instant windowEnd) {
      this.windowEnd = windowEnd;
    }
  }
}
