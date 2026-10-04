package com.labmarket.controller;

import com.labmarket.dto.ManualUsageRequest;
import com.labmarket.dto.PagedResponse;
import com.labmarket.dto.UsageLogResponse;
import com.labmarket.entity.UsageSource;
import com.labmarket.entity.UsageStatus;
import com.labmarket.service.UsageLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Usage history. Reads: own rows for students, everything (filterable) for
 * staff/admin. Writes: staff/admin MANUAL records only — there is deliberately
 * no update/delete endpoint; COMPLETED rows are immutable history.
 */
@RestController
@RequestMapping("/api/v1/usage")
@Tag(name = "Usage", description = "Usage history (read) and manual records (staff/admin)")
public class UsageLogController {

  private final UsageLogService usage;

  public UsageLogController(UsageLogService usage) {
    this.usage = usage;
  }

  @GetMapping
  @Operation(summary = "Usage history (own for students; filterable for staff/admin)")
  public ResponseEntity<PagedResponse<UsageLogResponse>> list(
      @RequestParam(required = false) Long equipmentId,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) UsageStatus status,
      @RequestParam(required = false) UsageSource source,
      @RequestParam(required = false) String laboratory,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @PageableDefault(size = 20, sort = "startedAt") Pageable pageable,
      Authentication authentication) {
    return ResponseEntity.ok(
        usage.list(
            authentication.getName(), equipmentId, userId, status, source, laboratory, from, to,
            pageable));
  }

  @GetMapping("/my")
  @Operation(summary = "Current user's own usage history")
  public ResponseEntity<PagedResponse<UsageLogResponse>> my(
      @RequestParam(required = false) Long equipmentId,
      @RequestParam(required = false) UsageStatus status,
      @RequestParam(required = false) UsageSource source,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @PageableDefault(size = 20, sort = "startedAt") Pageable pageable,
      Authentication authentication) {
    return ResponseEntity.ok(
        usage.my(authentication.getName(), equipmentId, status, source, from, to, pageable));
  }

  @GetMapping("/{id}")
  @Operation(summary = "One usage record (owner, staff or admin)")
  public ResponseEntity<UsageLogResponse> get(
      @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(usage.get(authentication.getName(), id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('LAB_STAFF', 'ADMIN')")
  @Operation(summary = "Record historical MANUAL usage (staff/admin; duration computed)")
  public ResponseEntity<UsageLogResponse> createManual(
      @Valid @RequestBody ManualUsageRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(usage.createManual(authentication.getName(), request));
  }
}
