package com.labmarket.controller;

import com.labmarket.dto.EvaluationResponse;
import com.labmarket.dto.PredictionResponse;
import com.labmarket.service.PredictionService;
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
 * Availability forecasts. Any authenticated user may predict; evaluation
 * (backtesting) is staff/admin. Every response carries its method and a
 * disclaimer: predictions inform booking decisions, they never decide them.
 */
@RestController
@RequestMapping("/api/v1/predictions")
@Tag(name = "Predictions", description = "Explainable availability forecasts")
public class PredictionController {

  private final PredictionService predictions;

  public PredictionController(PredictionService predictions) {
    this.predictions = predictions;
  }

  @GetMapping("/equipment/{equipmentId}")
  @Operation(summary = "Forecast availability per slot (?method=empirical|naive, default empirical)")
  public ResponseEntity<PredictionResponse> predict(
      @PathVariable Long equipmentId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(defaultValue = "60") int slotMinutes,
      @RequestParam(required = false) String method) {
    return ResponseEntity.ok(predictions.predict(equipmentId, from, to, slotMinutes, method));
  }

  @GetMapping("/equipment/{equipmentId}/evaluation")
  @PreAuthorize("hasAnyRole('VENDOR', 'ADMIN')")
  @Operation(summary = "Backtest past slots as-of their start vs what happened (staff/admin)")
  public ResponseEntity<EvaluationResponse> evaluate(
      @PathVariable Long equipmentId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(defaultValue = "60") int slotMinutes) {
    return ResponseEntity.ok(predictions.evaluate(equipmentId, from, to, slotMinutes));
  }
}
