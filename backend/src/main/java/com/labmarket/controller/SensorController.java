package com.labmarket.controller;

import com.labmarket.dto.SensorEventRequest;
import com.labmarket.dto.SensorEventResponse;
import com.labmarket.service.SensorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sensor ingest. Devices authenticate with {@code X-Sensor-Key} (per-equipment
 * key provisioned by staff) — NOT with user JWTs, which hardware cannot hold.
 * Event history is staff/admin only.
 */
@RestController
@RequestMapping("/api/v1/sensors")
@Tag(name = "Sensors", description = "Device event ingest (device key) and history (staff/admin)")
public class SensorController {

  private final SensorService sensors;

  public SensorController(SensorService sensors) {
    this.sensors = sensors;
  }

  @PostMapping("/events")
  @SecurityRequirements // device-key auth replaces the bearer token here
  @Operation(summary = "Ingest one sensor event (X-Sensor-Key header required)")
  public ResponseEntity<SensorEventResponse> ingest(
      @RequestHeader(value = "X-Sensor-Key", required = false) String sensorKey,
      @Valid @RequestBody SensorEventRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(sensors.ingest(sensorKey, request));
  }

  @GetMapping("/events")
  @PreAuthorize("hasAnyRole('VENDOR', 'ADMIN')")
  @Operation(summary = "Recent events for one equipment item (staff/admin)")
  public ResponseEntity<Page<SensorEventResponse>> history(
      @RequestParam String equipmentCode,
      @PageableDefault(size = 50, sort = "occurredAt") Pageable pageable) {
    return ResponseEntity.ok(sensors.history(equipmentCode, pageable));
  }
}
