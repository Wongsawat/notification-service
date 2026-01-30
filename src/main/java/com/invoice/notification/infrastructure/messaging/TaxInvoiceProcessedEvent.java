package com.invoice.notification.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when tax invoice processing is completed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxInvoiceProcessedEvent implements Serializable {

    private UUID eventId;
    private Instant occurredAt;
    private String eventType;
    private int version;
    private String invoiceId;
    private String invoiceNumber;
    private BigDecimal total;
    private String currency;
    private String correlationId;
}
