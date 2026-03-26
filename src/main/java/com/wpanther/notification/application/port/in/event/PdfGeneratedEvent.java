package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when PDF generation is completed
 */
@Getter
public class PdfGeneratedEvent extends TraceEvent {

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

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public PdfGeneratedEvent(String invoiceId, String invoiceNumber, String documentId,
                              String documentUrl, long fileSize, boolean xmlEmbedded,
                              boolean digitallySigned, String correlationId) {
        super(invoiceId, correlationId, "pdf-generation-service", "PDF_GENERATED", null);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentId = documentId;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
        this.digitallySigned = digitallySigned;
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
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("documentUrl") String documentUrl,
        @JsonProperty("fileSize") long fileSize,
        @JsonProperty("xmlEmbedded") boolean xmlEmbedded,
        @JsonProperty("digitallySigned") boolean digitallySigned
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentId = documentId;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
        this.digitallySigned = digitallySigned;
    }
}
