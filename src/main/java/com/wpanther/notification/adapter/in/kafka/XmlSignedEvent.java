package com.wpanther.notification.adapter.in.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when XML document signing is completed.
 * Consumed from xml.signed topic.
 */
@Getter
public class XmlSignedEvent extends TraceEvent {

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("correlationId")
    private final String correlationId;

    public XmlSignedEvent(String invoiceId, String invoiceNumber,
                          String documentType, String correlationId) {
        super(correlationId, "xml-signing-service", "XML_SIGNED");
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.correlationId = correlationId;
    }

    @JsonCreator
    public XmlSignedEvent(
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
        @JsonProperty("documentType") String documentType,
        @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, source, traceType, context);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.correlationId = correlationId;
    }
}
