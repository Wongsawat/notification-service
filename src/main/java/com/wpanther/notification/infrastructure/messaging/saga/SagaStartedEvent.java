package com.wpanther.notification.infrastructure.messaging.saga;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga lifecycle event: Saga orchestration started.
 * Published by orchestrator-service to saga.lifecycle.started topic.
 *
 * Consumed by notification-service for logging/monitoring.
 * No email notification created (per user requirement).
 */
@Getter
public class SagaStartedEvent extends TraceEvent {

    @JsonProperty("correlationId")
    private final String correlationId;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("currentStep")
    private final String currentStep;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("startedAt")
    private final Instant startedAt;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public SagaStartedEvent(String sagaId, String correlationId, String documentType,
                            String documentId, String currentStep, String invoiceNumber,
                            Instant startedAt) {
        super(sagaId, "orchestrator-service", "SAGA_STARTED");
        this.correlationId = correlationId;
        this.documentType = documentType;
        this.documentId = documentId;
        this.currentStep = currentStep;
        this.invoiceNumber = invoiceNumber;
        this.startedAt = startedAt;
    }

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     */
    @JsonCreator
    public SagaStartedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("currentStep") String currentStep,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("startedAt") Instant startedAt
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, source, traceType, context);
        this.correlationId = correlationId;
        this.documentType = documentType;
        this.documentId = documentId;
        this.currentStep = currentStep;
        this.invoiceNumber = invoiceNumber;
        this.startedAt = startedAt;
    }
}
