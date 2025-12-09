package com.invoice.notification.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event published when invoice is received
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceReceivedEvent implements Serializable {

    private String eventId;
    private String invoiceId;
    private String invoiceNumber;
    private String source;
    private LocalDateTime receivedAt;
    private String correlationId;
}
