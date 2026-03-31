package com.wpanther.notification.application.port.in.event.saga;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga lifecycle event: Saga orchestration completed successfully.
 * Published by orchestrator-service to saga.lifecycle.completed topic.
 *
 * Consumed by notification-service to create email notification.
 * This indicates successful end-to-end document processing.
 */
@Getter
public class SagaCompletedEvent extends TraceEvent {

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("stepsExecuted")
    private final Integer stepsExecuted;

    @JsonProperty("startedAt")
    private final Instant startedAt;

    @JsonProperty("completedAt")
    private final Instant completedAt;

    @JsonProperty("durationMs")
    private final Long durationMs;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public SagaCompletedEvent(String sagaId, String correlationId, String documentType,
                              String documentId, String documentNumber, Integer stepsExecuted,
                              Instant startedAt, Instant completedAt, Long durationMs) {
        super(sagaId, correlationId, "orchestrator-service", "SAGA_COMPLETED", null);
        this.documentType = documentType;
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.stepsExecuted = stepsExecuted;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
    }

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     */
    @JsonCreator
    public SagaCompletedEvent(
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
        @JsonProperty("documentNumber") String documentNumber,
        @JsonProperty("stepsExecuted") Integer stepsExecuted,
        @JsonProperty("startedAt") Instant startedAt,
        @JsonProperty("completedAt") Instant completedAt,
        @JsonProperty("durationMs") Long durationMs
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentType = documentType;
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.stepsExecuted = stepsExecuted;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.durationMs = durationMs;
    }
}
