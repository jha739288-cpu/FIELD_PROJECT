package com.labmarket.controller;

import com.labmarket.dto.HealthResponse;
import com.labmarket.util.DateTimeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated liveness endpoint for load balancers, Docker healthchecks and smoke tests. */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Service liveness (no authentication required)")
public class HealthController {

  private static final Logger log = LoggerFactory.getLogger(HealthController.class);

  @GetMapping
  @Operation(summary = "Service liveness probe")
  public ResponseEntity<HealthResponse> health() {
    log.debug("Health probe requested");
    return ResponseEntity.ok(new HealthResponse("UP", "labmarket-backend", DateTimeUtils.utcNow()));
  }
}
