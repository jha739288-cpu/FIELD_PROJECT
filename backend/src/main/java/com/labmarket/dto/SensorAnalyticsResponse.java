package com.labmarket.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sensor analytics over events that occurred in {@code [from, to)}.
 *
 * <p>{@code reliability} is the share of stored events that arrived on time
 * ({@code 1 - stale/total}); it is {@code null} when no events exist. Rejected
 * payloads (bad auth, bad values) are never stored, so they cannot appear here —
 * see {@code docs/evaluation/dashboard-metrics.md}.
 */
public record SensorAnalyticsResponse(
    Instant from,
    Instant to,
    long total,
    Map<String, Long> byStatus,
    long stale,
    Double reliability,
    long faults,
    long offline,
    List<EquipmentSensorCount> byEquipment) {

  public record EquipmentSensorCount(String equipmentCode, long count) {}
}
