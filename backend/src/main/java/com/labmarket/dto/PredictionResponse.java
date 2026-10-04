package com.labmarket.dto;

import com.labmarket.entity.PredictedStatus;
import java.time.Instant;
import java.util.List;

/**
 * Forecast for one item. A prediction aid — check the disclaimer, not a guarantee.
 */
public record PredictionResponse(
    Long equipmentId,
    String equipmentCode,
    String method,
    Instant from,
    Instant to,
    int slotMinutes,
    String disclaimer,
    List<SlotForecast> slots) {

  public record SlotForecast(
      Instant startTime,
      Instant endTime,
      PredictedStatus predictedStatus,
      double probabilityAvailable,
      double confidence,
      List<FactorView> factors) {}

  public record FactorView(String name, double value, String detail) {}
}
