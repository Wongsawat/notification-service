package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
public class DebitCreditNoteProcessedEvent extends TraceEvent {
    private static final String EVENT_TYPE = "debitcreditnote.processed";
    private static final String SOURCE = "debitcreditnote-processing-service";
    private static final String TRACE_TYPE = "DEBIT_CREDIT_NOTE_PROCESSED";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("amount")
    private final BigDecimal amount;

    @JsonProperty("currency")
    private final String currency;

    public DebitCreditNoteProcessedEvent(String documentId, String documentNumber,
                                          BigDecimal amount, String currency,
                                          String sagaId, String correlationId) {
        super(sagaId, correlationId, SOURCE, TRACE_TYPE, null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.amount = amount;
        this.currency = currency;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @JsonCreator
    public DebitCreditNoteProcessedEvent(
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
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("currency") String currency
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.amount = amount;
        this.currency = currency;
    }
}
