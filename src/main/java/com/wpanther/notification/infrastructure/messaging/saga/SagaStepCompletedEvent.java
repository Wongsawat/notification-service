package com.wpanther.notification.infrastructure.messaging.saga;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Saga lifecycle event: Individual saga step completed successfully.
 * Published by orchestrator-service to saga.lifecycle.step-completed topic.
 *
 * Consumed by notification-service for logging/monitoring only.
 * No email notification created (per user requirement - too noisy).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStepCompletedEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("occurredAt")
    private Instant occurredAt;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("version")
    private Integer version;

    @JsonProperty("sagaId")
    private String sagaId;

    @JsonProperty("correlationId")
    private String correlationId;

    @JsonProperty("documentType")
    private String documentType;

    @JsonProperty("documentId")
    private String documentId;

    @JsonProperty("completedStep")
    private String completedStep;

    @JsonProperty("nextStep")
    private String nextStep;

    @JsonProperty("invoiceNumber")
    private String invoiceNumber;

    @JsonProperty("stepDurationMs")
    private Long stepDurationMs;
}
