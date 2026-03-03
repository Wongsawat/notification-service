package com.wpanther.notification.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when invoice processing is completed
 */
@Getter
public class InvoiceProcessedEvent extends TraceEvent {

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("totalAmount")
    private final BigDecimal totalAmount;

    @JsonProperty("currency")
    private final String currency;

    @JsonProperty("correlationId")
    private final String correlationId;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public InvoiceProcessedEvent(String invoiceId, String invoiceNumber,
                                  BigDecimal totalAmount, String currency,
                                  String correlationId) {
        super(invoiceId, "invoice-processing-service", "INVOICE_PROCESSED");
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.correlationId = correlationId;
    }

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     * sagaId/source/traceType/context are TraceEvent fields — may be null for older events.
     */
    @JsonCreator
    public InvoiceProcessedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("totalAmount") BigDecimal totalAmount,
        @JsonProperty("currency") String currency,
        @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, source, traceType, context);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.correlationId = correlationId;
    }
}
