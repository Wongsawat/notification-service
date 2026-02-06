package com.wpanther.notification.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when PDF signing is completed by pdf-signing-service.
 *
 * This event contains the signed PDF URL and signing metadata.
 */
@Getter
public class PdfSignedEvent extends IntegrationEvent {

    // Document identifiers
    @JsonProperty("correlationId")
    private final String correlationId;

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

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
    public PdfSignedEvent(String correlationId, String invoiceId, String invoiceNumber,
                          String documentType, String signedDocumentId, String signedPdfUrl,
                          Long signedPdfSize, String transactionId, String certificate,
                          String signatureLevel, Instant signatureTimestamp) {
        super();
        this.correlationId = correlationId;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
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
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("signedDocumentId") String signedDocumentId,
        @JsonProperty("signedPdfUrl") String signedPdfUrl,
        @JsonProperty("signedPdfSize") Long signedPdfSize,
        @JsonProperty("transactionId") String transactionId,
        @JsonProperty("certificate") String certificate,
        @JsonProperty("signatureLevel") String signatureLevel,
        @JsonProperty("signatureTimestamp") Instant signatureTimestamp
    ) {
        super(eventId, occurredAt, eventType, version);
        this.correlationId = correlationId;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
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
