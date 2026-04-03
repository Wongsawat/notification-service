package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published by invoice-pdf-generation-service when invoice PDF generation completes.
 * Consumed from topic {@code pdf.generated.invoice}.
 */
@Getter
public class InvoicePdfGeneratedEvent extends TraceEvent {

    @JsonProperty("documentNumber")
    private final String documentNumber;

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

    public InvoicePdfGeneratedEvent(String documentId, String documentNumber,
                                    String documentUrl, long fileSize, boolean xmlEmbedded,
                                    boolean digitallySigned, String correlationId) {
        super(documentId, correlationId, "pdf-generation-service", "PDF_GENERATED", null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
        this.digitallySigned = digitallySigned;
    }

    @JsonCreator
    public InvoicePdfGeneratedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("documentNumber") String documentNumber,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("documentUrl") String documentUrl,
        @JsonProperty("fileSize") long fileSize,
        @JsonProperty("xmlEmbedded") boolean xmlEmbedded,
        @JsonProperty("digitallySigned") boolean digitallySigned
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentNumber = documentNumber;
        this.documentId = documentId;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
        this.digitallySigned = digitallySigned;
    }
}
