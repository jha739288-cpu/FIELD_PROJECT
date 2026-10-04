package com.labmarket.scheduling;

import com.labmarket.service.OverdueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic overdue sweep. Disabled in tests
 * ({@code app.overdue.scheduler-enabled=false}) so detection runs only when
 * tests invoke it explicitly — no timing flakes, no manual DB edits.
 */
@Component
@ConditionalOnProperty(
    name = "app.overdue.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OverdueScheduledJob {

  private static final Logger log = LoggerFactory.getLogger(OverdueScheduledJob.class);

  private final OverdueService overdue;

  public OverdueScheduledJob(OverdueService overdue) {
    this.overdue = overdue;
  }

  @Scheduled(fixedDelayString = "${app.overdue.check-interval-ms:300000}")
  public void run() {
    log.debug("Overdue sweep starting");
    int detected = overdue.detectOverdue();
    log.info("Overdue sweep finished: {} newly overdue booking(s)", detected);
  }
}
