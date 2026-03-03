package com.wpanther.notification.application.dto.event.saga;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga lifecycle event: Individual saga step completed successfully.
 * Published by orchestrator-service to saga.lifecycle.step-completed topic.
 *
 * Consumed by notification-service for logging/monitoring only.
 * No email notification created (per user requirement - too noisy).
 */
@Getter
public class SagaStepCompletedEvent extends TraceEvent {

    @JsonProperty("correlationId")
    private final String correlationId;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("completedStep")
    private final String completedStep;

    @JsonProperty("nextStep")
    private final String nextStep;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("stepDurationMs")
    private final Long stepDurationMs;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public SagaStepCompletedEvent(String sagaId, String correlationId, String documentType,
                                   String documentId, String completedStep, String nextStep,
                                   String invoiceNumber, Long stepDurationMs) {
        super(sagaId, "orchestrator-service", "SAGA_STEP_COMPLETED");
        this.correlationId = correlationId;
        this.documentType = documentType;
        this.documentId = documentId;
        this.completedStep = completedStep;
        this.nextStep = nextStep;
        this.invoiceNumber = invoiceNumber;
        this.stepDurationMs = stepDurationMs;
    }

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     */
    @JsonCreator
    public SagaStepCompletedEvent(
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
        @JsonProperty("completedStep") String completedStep,
        @JsonProperty("nextStep") String nextStep,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("stepDurationMs") Long stepDurationMs
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, source, traceType, context);
        this.correlationId = correlationId;
        this.documentType = documentType;
        this.documentId = documentId;
        this.completedStep = completedStep;
        this.nextStep = nextStep;
        this.invoiceNumber = invoiceNumber;
        this.stepDurationMs = stepDurationMs;
    }
}
