package com.wpanther.notification.infrastructure.messaging.saga;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Saga lifecycle event: Saga orchestration failed.
 * Published by orchestrator-service to saga.lifecycle.failed topic.
 *
 * Consumed by notification-service to create URGENT email notification.
 * This indicates a critical failure requiring immediate attention.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaFailedEvent {

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

    @JsonProperty("failedStep")
    private String failedStep;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("retryCount")
    private Integer retryCount;

    @JsonProperty("compensationInitiated")
    private Boolean compensationInitiated;

    @JsonProperty("startedAt")
    private Instant startedAt;

    @JsonProperty("failedAt")
    private Instant failedAt;

    @JsonProperty("durationMs")
    private Long durationMs;
}
