package com.invoice.notification.infrastructure.messaging.saga;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Saga lifecycle event: Saga orchestration started.
 * Published by orchestrator-service to saga.lifecycle.started topic.
 *
 * Consumed by notification-service for logging/monitoring.
 * No email notification created (per user requirement).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaStartedEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("occurredAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'", timezone = "UTC")
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

    @JsonProperty("currentStep")
    private String currentStep;

    @JsonProperty("invoiceNumber")
    private String invoiceNumber;

    @JsonProperty("startedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'", timezone = "UTC")
    private Instant startedAt;
}
