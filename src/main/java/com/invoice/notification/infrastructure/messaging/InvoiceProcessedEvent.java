package com.invoice.notification.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event published when invoice processing is completed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceProcessedEvent implements Serializable {

    private String eventId;
    private String invoiceId;
    private String invoiceNumber;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDateTime processedAt;
    private String correlationId;
}
