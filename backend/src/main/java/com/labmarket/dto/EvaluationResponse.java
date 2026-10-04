package com.labmarket.dto;

import java.time.Instant;

/**
 * Backtest outcome: past slots forecast as-of their start, compared with what
 * actually happened. A consistency check on recorded data — not a claim about
 * future accuracy. Requires enough history (see {@code note} when refused).
 */
public record EvaluationResponse(
    Long equipmentId,
    String method,
    Instant from,
    Instant to,
    int slotsEvaluated,
    int correct,
    double accuracy,
    double precisionUnavailable,
    double recallUnavailable,
    double brierScore,
    String note) {}
