package com.wpanther.notification.application.dto;

import java.util.Map;

/**
 * Response DTO for GET /api/v1/notifications/statistics/intake
 */
public record DocumentIntakeStatsResponse(
    Map<String, Long> statusCounts,
    Map<String, Long> documentTypeCounts
) {}
