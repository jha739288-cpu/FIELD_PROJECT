package com.labmarket.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Controllable clock. Production uses system UTC; tests substitute a fixed
 * clock so overdue/scheduling behaviour is deterministic without waiting.
 */
@Configuration
public class TimeConfig {

  @Bean
  public Clock appClock() {
    return Clock.systemUTC();
  }
}
