package com.wpanther.notification.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a document is received (before validation).
 * This lightweight event is used for counting all received documents regardless of validation outcome.
 *
 * Consumed by notification-service to track total received document count.
 */
@Getter
public class DocumentReceivedCountingEvent extends TraceEvent {

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("correlationId")
    private final String correlationId;

    @JsonProperty("receivedAt")
    private final Instant receivedAt;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public DocumentReceivedCountingEvent(String documentId, String correlationId, Instant receivedAt) {
        super(documentId, "document-intake-service", "DOCUMENT_RECEIVED_COUNTING");
        this.documentId = documentId;
        this.correlationId = correlationId;
        this.receivedAt = receivedAt;
    }

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     */
    @JsonCreator
    public DocumentReceivedCountingEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("receivedAt") Instant receivedAt
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, source, traceType, context);
        this.documentId = documentId;
        this.correlationId = correlationId;
        this.receivedAt = receivedAt;
    }
}
