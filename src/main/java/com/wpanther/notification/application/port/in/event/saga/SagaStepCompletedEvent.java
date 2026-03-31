package com.wpanther.notification.application.port.in.event.saga;

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

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("completedStep")
    private final String completedStep;

    @JsonProperty("nextStep")
    private final String nextStep;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public SagaStepCompletedEvent(String sagaId, String correlationId, String documentType,
                                   String completedStep, String nextStep) {
        super(sagaId, correlationId, "orchestrator-service", "SAGA_STEP_COMPLETED", null);
        this.documentType = documentType;
        this.completedStep = completedStep;
        this.nextStep = nextStep;
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
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("completedStep") String completedStep,
        @JsonProperty("nextStep") String nextStep
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentType = documentType;
        this.completedStep = completedStep;
        this.nextStep = nextStep;
    }
}
