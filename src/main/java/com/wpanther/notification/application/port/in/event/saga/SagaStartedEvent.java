package com.wpanther.notification.application.port.in.event.saga;

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

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("currentStep")
    private final String currentStep;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public SagaStartedEvent(String sagaId, String correlationId, String documentType,
                            String documentId, String currentStep, String documentNumber) {
        super(sagaId, correlationId, "orchestrator", "SAGA_STARTED", null);
        this.documentType = documentType;
        this.documentId = documentId;
        this.currentStep = currentStep;
        this.documentNumber = documentNumber;
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
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("currentStep") String currentStep,
        @JsonProperty("documentNumber") String documentNumber
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentType = documentType;
        this.documentId = documentId;
        this.currentStep = currentStep;
        this.documentNumber = documentNumber;
    }
}
