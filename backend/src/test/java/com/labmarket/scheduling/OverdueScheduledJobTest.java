package com.labmarket.scheduling;

import static org.mockito.Mockito.verify;

import com.labmarket.service.OverdueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The job is a thin trigger: detection logic itself is tested elsewhere. */
@ExtendWith(MockitoExtension.class)
class OverdueScheduledJobTest {

  @Mock private OverdueService overdue;

  @Test
  void runDelegatesToDetection() {
    new OverdueScheduledJob(overdue).run();

    verify(overdue).detectOverdue();
  }
}
