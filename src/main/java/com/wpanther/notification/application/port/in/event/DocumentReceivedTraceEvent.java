package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Local DTO mirroring the JSON shape published by document-intake-service
 * on the trace.document.received topic.
 *
 * <p>The notification service must NOT import classes from document-intake-service,
 * so this class duplicates the field layout without coupling to the producer's code.</p>
 *
 * <p>The producer JSON does NOT include {@code sagaId}, {@code traceType}, or {@code context}.
 * The {@code @JsonIgnoreProperties(ignoreUnknown = true)} annotation (inherited from
 * {@link TraceEvent}) ensures those fields are silently ignored when the producer
 * omits them, and any extra fields added in the future will also be ignored.</p>
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentReceivedTraceEvent extends TraceEvent {

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("status")
    private final String status;

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     * Accepts all parent fields (including sagaId, traceType, context that may be absent)
     * and the document-specific fields.
     */
    @JsonCreator
    public DocumentReceivedTraceEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("documentNumber") String documentNumber,
        @JsonProperty("status") String status
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.status = status;
    }
}
