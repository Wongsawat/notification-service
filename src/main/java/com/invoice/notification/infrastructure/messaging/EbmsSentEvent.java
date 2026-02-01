package com.invoice.notification.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event published when a document is successfully submitted to the
 * Thailand Revenue Department via ebMS protocol.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EbmsSentEvent implements Serializable {

    private String eventId;
    private String occurredAt;
    private String eventType;
    private String version;
    private String documentId;
    private String invoiceId;
    private String invoiceNumber;
    private String documentType;
    private String ebmsMessageId;
    private LocalDateTime sentAt;
    private String correlationId;
}
