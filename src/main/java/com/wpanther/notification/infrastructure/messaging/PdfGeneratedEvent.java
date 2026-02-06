package com.wpanther.notification.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when PDF generation is completed
 */
@Getter
public class PdfGeneratedEvent extends IntegrationEvent {

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentUrl")
    private final String documentUrl;

    @JsonProperty("fileSize")
    private final long fileSize;

    @JsonProperty("xmlEmbedded")
    private final boolean xmlEmbedded;

    @JsonProperty("digitallySigned")
    private final boolean digitallySigned;

    @JsonProperty("correlationId")
    private final String correlationId;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public PdfGeneratedEvent(String invoiceId, String invoiceNumber, String documentId,
                              String documentUrl, long fileSize, boolean xmlEmbedded,
                              boolean digitallySigned, String correlationId) {
        super();
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentId = documentId;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
        this.digitallySigned = digitallySigned;
        this.correlationId = correlationId;
    }

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     */
    @JsonCreator
    public PdfGeneratedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("documentUrl") String documentUrl,
        @JsonProperty("fileSize") long fileSize,
        @JsonProperty("xmlEmbedded") boolean xmlEmbedded,
        @JsonProperty("digitallySigned") boolean digitallySigned,
        @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentId = documentId;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
        this.digitallySigned = digitallySigned;
        this.correlationId = correlationId;
    }
}
