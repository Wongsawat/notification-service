package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a document is received and validated successfully.
 * This event contains full document details and is routed to document-type-specific topics.
 *
 * Consumed by:
 * - notification-service: For tracking type-specific statistics
 * - invoice-processing-service / taxinvoice-processing-service: For downstream processing
 */
@Getter
public class DocumentReceivedEvent extends TraceEvent {

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("xmlContent")
    private final String xmlContent;

    @JsonProperty("documentType")
    private final String documentType;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public DocumentReceivedEvent(String documentId, String documentNumber, String xmlContent,
                                  String correlationId, String documentType) {
        super(documentId, correlationId, "document-intake-service", "DOCUMENT_RECEIVED", null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.xmlContent = xmlContent;
        this.documentType = documentType;
    }

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     */
    @JsonCreator
    public DocumentReceivedEvent(
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
        @JsonProperty("xmlContent") String xmlContent,
        @JsonProperty("documentType") String documentType
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.xmlContent = xmlContent;
        this.documentType = documentType;
    }
}
