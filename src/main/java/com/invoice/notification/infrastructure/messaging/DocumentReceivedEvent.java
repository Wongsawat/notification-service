package com.invoice.notification.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Event published when a document is received and validated successfully.
 * This event contains full document details and is routed to document-type-specific topics.
 *
 * Consumed by:
 * - notification-service: For tracking type-specific statistics
 * - invoice-processing-service / taxinvoice-processing-service: For downstream processing
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentReceivedEvent implements Serializable {

    private String eventId;
    private String eventType;
    private String occurredAt;
    private int version;
    private String documentId;
    private String invoiceNumber;
    private String xmlContent;
    private String correlationId;
    private String documentType;
}
