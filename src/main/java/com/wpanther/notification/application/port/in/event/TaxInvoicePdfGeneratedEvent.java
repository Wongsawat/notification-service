package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published by taxinvoice-pdf-generation-service when tax invoice PDF generation completes.
 * Consumed from topic {@code pdf.generated.tax-invoice}.
 */
@Getter
public class TaxInvoicePdfGeneratedEvent extends TraceEvent {

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("documentUrl")
    private final String documentUrl;

    @JsonProperty("fileSize")
    private final long fileSize;

    @JsonProperty("xmlEmbedded")
    private final boolean xmlEmbedded;

    /**
     * Constructor for creating new events.
     */
    public TaxInvoicePdfGeneratedEvent(String sagaId, String documentId,
                                       String documentNumber, String documentUrl, long fileSize,
                                       boolean xmlEmbedded, String correlationId) {
        super(sagaId, correlationId, "taxinvoice-pdf-generation-service", "PDF_GENERATED", null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
    }

    /**
     * Constructor for deserialization from JSON (Kafka).
     */
    @JsonCreator
    public TaxInvoicePdfGeneratedEvent(
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
        @JsonProperty("documentUrl") String documentUrl,
        @JsonProperty("fileSize") long fileSize,
        @JsonProperty("xmlEmbedded") boolean xmlEmbedded
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
    }
}
