package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when PDF signing is completed by pdf-signing-service.
 *
 * This event contains the signed PDF URL and signing metadata.
 */
@Getter
public class PdfSignedEvent extends TraceEvent {

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("documentType")
    private final String documentType;  // INVOICE, TAX_INVOICE, etc.

    // Signed document details
    @JsonProperty("signedDocumentId")
    private final String signedDocumentId;

    @JsonProperty("signedPdfUrl")
    private final String signedPdfUrl;

    @JsonProperty("signedPdfSize")
    private final Long signedPdfSize;

    // Signing metadata
    @JsonProperty("transactionId")
    private final String transactionId;

    @JsonProperty("certificate")
    private final String certificate;

    @JsonProperty("signatureLevel")
    private final String signatureLevel;

    @JsonProperty("signatureTimestamp")
    private final Instant signatureTimestamp;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public PdfSignedEvent(String sagaId, String correlationId, String documentId, String documentNumber,
                          String documentType, String signedDocumentId, String signedPdfUrl,
                          Long signedPdfSize, String transactionId, String certificate,
                          String signatureLevel, Instant signatureTimestamp) {
        super(sagaId, correlationId, "pdf-signing-service", "PDF_SIGNED", null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
        this.signedDocumentId = signedDocumentId;
        this.signedPdfUrl = signedPdfUrl;
        this.signedPdfSize = signedPdfSize;
        this.transactionId = transactionId;
        this.certificate = certificate;
        this.signatureLevel = signatureLevel;
        this.signatureTimestamp = signatureTimestamp;
    }

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     */
    @JsonCreator
    public PdfSignedEvent(
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
        @JsonProperty("signedDocumentId") String signedDocumentId,
        @JsonProperty("signedPdfUrl") String signedPdfUrl,
        @JsonProperty("signedPdfSize") Long signedPdfSize,
        @JsonProperty("transactionId") String transactionId,
        @JsonProperty("certificate") String certificate,
        @JsonProperty("signatureLevel") String signatureLevel,
        @JsonProperty("signatureTimestamp") Instant signatureTimestamp
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
        this.signedDocumentId = signedDocumentId;
        this.signedPdfUrl = signedPdfUrl;
        this.signedPdfSize = signedPdfSize;
        this.transactionId = transactionId;
        this.certificate = certificate;
        this.signatureLevel = signatureLevel;
        this.signatureTimestamp = signatureTimestamp;
    }
}
