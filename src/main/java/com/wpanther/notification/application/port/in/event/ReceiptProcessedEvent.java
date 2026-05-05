package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class ReceiptProcessedEvent extends TraceEvent {
    private static final String EVENT_TYPE = "receipt.processed";
    private static final String SOURCE = "receipt-processing-service";
    private static final String TRACE_TYPE = "RECEIPT_PROCESSED";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("totalAmount")
    private final BigDecimal totalAmount;

    @JsonProperty("currency")
    private final String currency;

    public ReceiptProcessedEvent(String documentId, String documentNumber,
                                  BigDecimal totalAmount, String currency,
                                  String sagaId, String correlationId) {
        super(sagaId, correlationId, SOURCE, TRACE_TYPE, null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.totalAmount = totalAmount;
        this.currency = currency;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @JsonCreator
    public ReceiptProcessedEvent(
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
        @JsonProperty("totalAmount") BigDecimal totalAmount,
        @JsonProperty("currency") String currency
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.totalAmount = totalAmount;
        this.currency = currency;
    }
}
