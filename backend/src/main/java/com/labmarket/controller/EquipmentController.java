package com.labmarket.controller;

import com.labmarket.dto.EquipmentCreateRequest;
import com.labmarket.dto.EquipmentResponse;
import com.labmarket.dto.EquipmentUpdateRequest;
import com.labmarket.dto.PagedResponse;
import com.labmarket.dto.SensorKeyResponse;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.service.EquipmentService;
import com.labmarket.service.SensorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Equipment catalog. Reads: any authenticated user (STUDENT included).
 * Mutations: LAB_STAFF and ADMIN only.
 */
@RestController
@RequestMapping("/api/v1/equipment")
@Tag(name = "Equipment", description = "Laboratory equipment catalog")
public class EquipmentController {

  private final EquipmentService equipment;
  private final SensorService sensors;

  public EquipmentController(EquipmentService equipment, SensorService sensors) {
    this.equipment = equipment;
    this.sensors = sensors;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('LAB_STAFF', 'ADMIN')")
  @Operation(summary = "Register equipment (staff/admin, 201 on success)")
  public ResponseEntity<EquipmentResponse> create(
      @Valid @RequestBody EquipmentCreateRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(equipment.create(request, authentication.getName()));
  }

  @GetMapping
  @Operation(summary = "Search equipment with pagination, status/category filters and free text")
  public ResponseEntity<PagedResponse<EquipmentResponse>> list(
      @RequestParam(required = false) EquipmentStatus status,
      @RequestParam(required = false) String category,
      @RequestParam(required = false, name = "q") String query,
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    return ResponseEntity.ok(equipment.list(status, category, query, pageable));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get one equipment item by id")
  public ResponseEntity<EquipmentResponse> get(@PathVariable Long id) {
    return ResponseEntity.ok(equipment.get(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('LAB_STAFF', 'ADMIN')")
  @Operation(summary = "Full update of an equipment item (staff/admin; code is immutable)")
  public ResponseEntity<EquipmentResponse> update(
      @PathVariable Long id, @Valid @RequestBody EquipmentUpdateRequest request) {
    return ResponseEntity.ok(equipment.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('LAB_STAFF', 'ADMIN')")
  @Operation(summary = "Delete equipment (staff/admin; blocked while in use, 204 on success)")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    equipment.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/sensor-key")
  @PreAuthorize("hasAnyRole('LAB_STAFF', 'ADMIN')")
  @Operation(summary = "Provision (or rotate) the device key for one item (staff/admin; raw key shown once)")
  public ResponseEntity<SensorKeyResponse> provisionSensorKey(
      @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(sensors.provisionKey(authentication.getName(), id));
  }
}
