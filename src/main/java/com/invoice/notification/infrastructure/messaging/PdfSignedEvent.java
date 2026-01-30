package com.invoice.notification.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event published when PDF signing is completed by pdf-signing-service.
 *
 * This event contains the signed PDF URL and signing metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfSignedEvent implements Serializable {

    // Base event fields
    private String eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private String version;
    private String correlationId;

    // Document identifiers
    private String invoiceId;
    private String invoiceNumber;
    private String documentType;  // INVOICE, TAX_INVOICE, etc.

    // Signed document details
    private String signedDocumentId;
    private String signedPdfUrl;
    private Long signedPdfSize;

    // Signing metadata
    private String transactionId;
    private String certificate;
    private String signatureLevel;
    private LocalDateTime signatureTimestamp;
}
