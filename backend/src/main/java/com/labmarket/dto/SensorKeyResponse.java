package com.labmarket.dto;

/** Device key, shown ONCE at provisioning — the backend stores only its hash. */
public record SensorKeyResponse(Long equipmentId, String equipmentCode, String sensorKey) {}
