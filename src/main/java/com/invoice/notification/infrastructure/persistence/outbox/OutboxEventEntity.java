package com.invoice.notification.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for OutboxEvent from saga-commons.
 * Maps domain OutboxEvent to outbox_events table in notification_db.
 *
 * This entity enables the Transactional Outbox Pattern:
 * - Events are stored in same database transaction as notification state changes
 * - Debezium CDC monitors this table and publishes to Kafka
 * - Guarantees at-least-once delivery with no message loss
 *
 * The entity supports two publishing modes:
 * 1. Polling-based: OutboxService polls PENDING events
 * 2. Debezium CDC: EventRouter transform routes to Kafka (recommended)
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status", columnList = "status"),
    @Index(name = "idx_outbox_created", columnList = "created_at"),
    @Index(name = "idx_outbox_aggregate", columnList = "aggregate_id, aggregate_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEventEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /**
     * Aggregate type (e.g., "Notification", "Invoice").
     * Used for querying events by business entity type.
     */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /**
     * Aggregate ID (e.g., notification UUID).
     * Used for querying events related to specific entity instance.
     */
    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    /**
     * Event type (e.g., "NotificationSentEvent").
     * Corresponds to Java class name of the event.
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * Event payload as JSON text.
     * Contains serialized IntegrationEvent with all fields.
     * Uses TEXT (not JSONB) for database portability.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * When event was created (persisted to outbox table).
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * When event was successfully published to Kafka.
     * NULL if not yet published.
     */
    @Column(name = "published_at")
    private Instant publishedAt;

    /**
     * Publication status: PENDING, PUBLISHED, or FAILED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    /**
     * Number of publication retry attempts.
     * Increments on each failure, resets on success.
     */
    @Column(name = "retry_count")
    private Integer retryCount;

    /**
     * Error message from last publication failure.
     * NULL if no error or successfully published.
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    // ==================== Debezium CDC Support ====================

    /**
     * Target Kafka topic for Debezium EventRouter.
     * Extracted by EventRouter transform: route.by.field=topic
     */
    @Column(name = "topic", length = 255)
    private String topic;

    /**
     * Kafka partition key for EventRouter.
     * Ensures events with same key go to same partition (ordering).
     */
    @Column(name = "partition_key", length = 255)
    private String partitionKey;

    /**
     * Additional Kafka headers as JSON text.
     * Contains correlationId, documentType, etc.
     */
    @Column(name = "headers", columnDefinition = "TEXT")
    private String headers;

    /**
     * JPA lifecycle callback - set defaults before first persist.
     */
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = OutboxStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }

    /**
     * Convert domain OutboxEvent to JPA entity.
     * Manual mapping (no MapStruct, consistent with notification-service pattern).
     *
     * @param event Domain OutboxEvent from saga-commons
     * @return JPA entity ready to persist
     */
    public static OutboxEventEntity fromDomain(OutboxEvent event) {
        return OutboxEventEntity.builder()
                .id(event.getId())
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .eventType(event.getEventType())
                .payload(event.getPayload())
                .createdAt(event.getCreatedAt())
                .publishedAt(event.getPublishedAt())
                .status(event.getStatus())
                .retryCount(event.getRetryCount())
                .errorMessage(event.getErrorMessage())
                .topic(event.getTopic())
                .partitionKey(event.getPartitionKey())
                .headers(event.getHeaders())
                .build();
    }

    /**
     * Convert JPA entity to domain OutboxEvent.
     * Used when reading from database.
     *
     * @return Domain OutboxEvent for saga-commons OutboxService
     */
    public OutboxEvent toDomain() {
        return OutboxEvent.builder()
                .id(this.id)
                .aggregateType(this.aggregateType)
                .aggregateId(this.aggregateId)
                .eventType(this.eventType)
                .payload(this.payload)
                .createdAt(this.createdAt)
                .publishedAt(this.publishedAt)
                .status(this.status)
                .retryCount(this.retryCount)
                .errorMessage(this.errorMessage)
                .topic(this.topic)
                .partitionKey(this.partitionKey)
                .headers(this.headers)
                .build();
    }
}
