package com.labmarket.controller;

import com.labmarket.dto.BookingAnalyticsResponse;
import com.labmarket.dto.EquipmentDashboardResponse;
import com.labmarket.dto.SensorAnalyticsResponse;
import com.labmarket.dto.SummaryResponse;
import com.labmarket.dto.UsageTrendResponse;
import com.labmarket.dto.UtilizationResponse;
import com.labmarket.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lab-wide analytics for LAB_STAFF and ADMIN only. Students use
 * {@code /api/v1/dashboard/my-summary} (personal scope).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('LAB_STAFF', 'ADMIN')")
@Tag(name = "Dashboard", description = "Analytics computed from live records")
public class DashboardController {

  private final DashboardService dashboard;

  public DashboardController(DashboardService dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping("/summary")
  @Operation(summary = "Fleet snapshot: status counts + trailing-30-day activity")
  public ResponseEntity<SummaryResponse> summary() {
    return ResponseEntity.ok(dashboard.summary());
  }

  @GetMapping("/utilization")
  @Operation(summary = "Utilization % over [from, to), overall and per item")
  public ResponseEntity<UtilizationResponse> utilization(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) Long equipmentId) {
    return ResponseEntity.ok(dashboard.utilization(from, to, equipmentId));
  }

  @GetMapping("/equipment/{id}")
  @Operation(summary = "One item: live status, windowed bookings/usage and recent reservations")
  public ResponseEntity<EquipmentDashboardResponse> equipment(
      @PathVariable Long id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return ResponseEntity.ok(dashboard.equipmentDashboard(id, from, to));
  }

  @GetMapping("/usage")
  @Operation(summary = "Daily usage trend over [from, to) (defaults: trailing 30 days)")
  public ResponseEntity<UsageTrendResponse> usage(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return ResponseEntity.ok(dashboard.usageTrend(from, to));
  }

  @GetMapping("/conflicts")
  @Operation(summary = "Rejected overlap attempts in [from, to) (defaults: trailing 30 days)")
  public ResponseEntity<ConflictCountResponse> conflicts(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return ResponseEntity.ok(new ConflictCountResponse(dashboard.conflictAttempts(from, to)));
  }

  @GetMapping("/bookings")
  @Operation(summary = "Booking analytics: totals, status mix, daily counts, per-item counts")
  public ResponseEntity<BookingAnalyticsResponse> bookings(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) Long equipmentId) {
    return ResponseEntity.ok(dashboard.bookingAnalytics(from, to, equipmentId));
  }

  @GetMapping("/sensors")
  @Operation(summary = "Sensor analytics: totals, status mix, freshness reliability, per-item counts")
  public ResponseEntity<SensorAnalyticsResponse> sensors(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false) Long equipmentId) {
    return ResponseEntity.ok(dashboard.sensorAnalytics(from, to, equipmentId));
  }

  public record ConflictCountResponse(long conflictAttempts) {}
}
