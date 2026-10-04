package com.labmarket.util;

import java.time.Instant;

/** Shared time helpers. All timestamps are UTC instants (TIMESTAMPTZ in Oracle). */
public final class DateTimeUtils {

  private DateTimeUtils() {}

  public static Instant utcNow() {
    return Instant.now();
  }
}
