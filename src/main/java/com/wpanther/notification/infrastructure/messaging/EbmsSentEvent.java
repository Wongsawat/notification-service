package com.wpanther.notification.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a document is successfully submitted to the
 * Thailand Revenue Department via ebMS protocol.
 */
@Getter
public class EbmsSentEvent extends IntegrationEvent {

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("ebmsMessageId")
    private final String ebmsMessageId;

    @JsonProperty("sentAt")
    private final Instant sentAt;

    @JsonProperty("correlationId")
    private final String correlationId;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public EbmsSentEvent(String documentId, String invoiceId, String invoiceNumber,
                          String documentType, String ebmsMessageId, Instant sentAt,
                          String correlationId) {
        super();
        this.documentId = documentId;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.ebmsMessageId = ebmsMessageId;
        this.sentAt = sentAt;
        this.correlationId = correlationId;
    }

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     */
    @JsonCreator
    public EbmsSentEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("ebmsMessageId") String ebmsMessageId,
        @JsonProperty("sentAt") Instant sentAt,
        @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version);
        this.documentId = documentId;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.ebmsMessageId = ebmsMessageId;
        this.sentAt = sentAt;
        this.correlationId = correlationId;
    }
}
