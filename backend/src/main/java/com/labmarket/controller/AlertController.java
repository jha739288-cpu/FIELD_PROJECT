package com.labmarket.controller;

import com.labmarket.dto.AlertResponse;
import com.labmarket.dto.PagedResponse;
import com.labmarket.dto.ResolveAlertRequest;
import com.labmarket.entity.AlertStatus;
import com.labmarket.entity.AlertType;
import com.labmarket.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Alerts are operational: VENDOR and ADMIN only. */
@RestController
@RequestMapping("/api/v1/alerts")
@PreAuthorize("hasAnyRole('VENDOR', 'ADMIN')")
@Tag(name = "Alerts", description = "Operational alerts (staff/admin)")
public class AlertController {

  private final AlertService alerts;

  public AlertController(AlertService alerts) {
    this.alerts = alerts;
  }

  @GetMapping
  @Operation(summary = "List alerts, optionally filtered by type/status")
  public ResponseEntity<PagedResponse<AlertResponse>> list(
      @RequestParam(required = false) AlertType type,
      @RequestParam(required = false) AlertStatus status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(alerts.list(type, status, pageable));
  }

  @GetMapping("/overdue")
  @Operation(summary = "Open OVERDUE alerts")
  public ResponseEntity<PagedResponse<AlertResponse>> overdue(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(alerts.overdue(pageable));
  }

  @PutMapping("/{id}/resolve")
  @Operation(summary = "Resolve an alert and remediate its booking/equipment")
  public ResponseEntity<AlertResponse> resolve(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) ResolveAlertRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(alerts.resolve(authentication.getName(), id, request));
  }
}
