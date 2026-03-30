package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a document is successfully submitted to the
 * Thailand Revenue Department via ebMS protocol.
 */
@Getter
public class EbmsSentEvent extends TraceEvent {

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("ebmsMessageId")
    private final String ebmsMessageId;

    @JsonProperty("sentAt")
    private final Instant sentAt;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public EbmsSentEvent(String documentId, String documentNumber,
                          String documentType, String ebmsMessageId, Instant sentAt,
                          String correlationId) {
        super(documentId, correlationId, "ebms-sending-service", "EBMS_SENT", null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
        this.ebmsMessageId = ebmsMessageId;
        this.sentAt = sentAt;
    }

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     */
    @JsonCreator
    public EbmsSentEvent(
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
        @JsonProperty("documentNumber") String documentNumber,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("ebmsMessageId") String ebmsMessageId,
        @JsonProperty("sentAt") Instant sentAt
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
        this.ebmsMessageId = ebmsMessageId;
        this.sentAt = sentAt;
    }
}
