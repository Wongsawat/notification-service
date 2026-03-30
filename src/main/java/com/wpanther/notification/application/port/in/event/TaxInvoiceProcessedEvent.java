package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when tax invoice processing is completed
 */
@Getter
public class TaxInvoiceProcessedEvent extends TraceEvent {

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("total")
    private final BigDecimal total;

    @JsonProperty("currency")
    private final String currency;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public TaxInvoiceProcessedEvent(String documentId, String documentNumber,
                                     BigDecimal total, String currency,
                                     String correlationId) {
        super(documentId, correlationId, "taxinvoice-processing-service", "TAXINVOICE_PROCESSED", null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.total = total;
        this.currency = currency;
    }

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     */
    @JsonCreator
    public TaxInvoiceProcessedEvent(
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
        @JsonProperty("total") BigDecimal total,
        @JsonProperty("currency") String currency
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.total = total;
        this.currency = currency;
    }
}
