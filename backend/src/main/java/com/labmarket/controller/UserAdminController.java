package com.labmarket.controller;

import com.labmarket.dto.PagedResponse;
import com.labmarket.dto.RolesUpdateRequest;
import com.labmarket.dto.StatusUpdateRequest;
import com.labmarket.dto.UserAdminResponse;
import com.labmarket.dto.VendorSummaryResponse;
import com.labmarket.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
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

/** Account administration. ADMIN only — enforced here and re-checked in the service. */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User admin", description = "Account search, activation and roles (admin)")
public class UserAdminController {

  private final UserAdminService users;

  public UserAdminController(UserAdminService users) {
    this.users = users;
  }

  @GetMapping
  @Operation(summary = "Search accounts (text, role, status)")
  public ResponseEntity<PagedResponse<UserAdminResponse>> list(
      @RequestParam(required = false, name = "q") String query,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) Boolean enabled,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(users.list(query, role, enabled, pageable));
  }

  @GetMapping("/{id}")
  @Operation(summary = "One account with booking count")
  public ResponseEntity<UserAdminResponse> get(@PathVariable Long id) {
    return ResponseEntity.ok(users.get(id));
  }

  @PutMapping("/{id}/status")
  @Operation(summary = "Activate/deactivate an account (never your own)")
  public ResponseEntity<UserAdminResponse> setStatus(
      @PathVariable Long id,
      @Valid @RequestBody StatusUpdateRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        users.setEnabled(authentication.getName(), id, request.enabled()));
  }

  @PutMapping("/{id}/roles")
  @Operation(summary = "Replace an account's roles (never your own)")
  public ResponseEntity<UserAdminResponse> setRoles(
      @PathVariable Long id,
      @Valid @RequestBody RolesUpdateRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(users.setRoles(authentication.getName(), id, request));
  }

  @GetMapping("/vendors")
  @Operation(summary = "Vendor accounts with equipment/booking counts")
  public ResponseEntity<PagedResponse<VendorSummaryResponse>> vendors(
      @RequestParam(required = false, name = "q") String query,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(users.vendors(query, pageable));
  }
}
