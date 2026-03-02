package com.wpanther.notification.application.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Structured error response for API errors
 */
public record ErrorResponse(
    Instant timestamp,
    int status,
    String message,
    Map<String, String> errors  // null for non-validation errors
) {
}
