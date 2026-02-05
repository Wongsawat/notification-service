package com.invoice.notification.infrastructure.messaging.saga;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Saga lifecycle event: Saga orchestration completed successfully.
 * Published by orchestrator-service to saga.lifecycle.completed topic.
 *
 * Consumed by notification-service to create email notification.
 * This indicates successful end-to-end document processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaCompletedEvent {

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

    @JsonProperty("invoiceNumber")
    private String invoiceNumber;

    @JsonProperty("stepsExecuted")
    private Integer stepsExecuted;

    @JsonProperty("startedAt")
    private Instant startedAt;

    @JsonProperty("completedAt")
    private Instant completedAt;

    @JsonProperty("durationMs")
    private Long durationMs;
}
