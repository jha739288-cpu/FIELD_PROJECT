package com.labmarket.controller;

import com.labmarket.dto.MySummaryResponse;
import com.labmarket.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Personal analytics for any authenticated user (students included):
 * own bookings and own usage only — never lab-wide numbers.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Analytics computed from live records")
public class MyDashboardController {

  private final DashboardService dashboard;

  public MyDashboardController(DashboardService dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping("/my-summary")
  @Operation(summary = "Current user's own bookings and usage (trailing 30 days)")
  public ResponseEntity<MySummaryResponse> mySummary(Authentication authentication) {
    return ResponseEntity.ok(dashboard.mySummary(authentication.getName()));
  }
}
