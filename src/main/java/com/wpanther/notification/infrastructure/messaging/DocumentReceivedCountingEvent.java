package com.wpanther.notification.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Event published when a document is received (before validation).
 * This lightweight event is used for counting all received documents regardless of validation outcome.
 *
 * Consumed by notification-service to track total received document count.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentReceivedCountingEvent implements Serializable {

    private String eventId;
    private String eventType;
    private Instant occurredAt;
    private int version;
    private String documentId;
    private String correlationId;
    private Instant receivedAt;
}
