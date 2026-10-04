package com.labmarket.dto;

import java.time.Instant;
import java.util.List;

/**
 * Utilization over {@code [from, to)}.
 * {@code utilizationPercent = 100 × usageSeconds ÷ (operationalEquipment × windowSeconds)},
 * where usage is merged per item (no double-counting), clipped to the window
 * (open sessions clamp at {@code min(now, to)}), and items whose maintenance
 * state is not OPERATIONAL are excluded from capacity but still listed.
 */
public record UtilizationResponse(
    Instant from,
    Instant to,
    int equipmentCount,
    int operationalCount,
    long windowSeconds,
    long totalUsageSeconds,
    double utilizationPercent,
    List<ItemUtilization> items) {

  public record ItemUtilization(
      Long equipmentId, String equipmentCode, String equipmentName, long usageSeconds,
      double utilizationPercent, boolean operational, String maintenanceStatus) {}
}
