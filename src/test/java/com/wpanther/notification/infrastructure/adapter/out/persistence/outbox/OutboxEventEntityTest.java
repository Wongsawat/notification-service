package com.wpanther.notification.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxEventEntity Tests")
class OutboxEventEntityTest {

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build entity with all fields")
        void testBuildEntityWithAllFields() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            // Act
            OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(id)
                .aggregateType("Notification")
                .aggregateId("notif-123")
                .eventType("NotificationSentEvent")
                .payload("{\"key\":\"value\"}")
                .createdAt(now)
                .publishedAt(now.plusSeconds(5))
                .status(OutboxStatus.PUBLISHED)
                .retryCount(0)
                .errorMessage(null)
                .topic("notification.sent")
                .partitionKey("notif-123")
                .headers("{\"correlationId\":\"corr-123\"}")
                .build();

            // Assert
            assertThat(entity).isNotNull();
            assertThat(entity.getId()).isEqualTo(id);
            assertThat(entity.getAggregateType()).isEqualTo("Notification");
            assertThat(entity.getAggregateId()).isEqualTo("notif-123");
            assertThat(entity.getEventType()).isEqualTo("NotificationSentEvent");
            assertThat(entity.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        }

        @Test
        @DisplayName("Should build entity with minimal fields")
        void testBuildEntityWithMinimalFields() {
            // Act
            OutboxEventEntity entity = OutboxEventEntity.builder()
                .aggregateType("Notification")
                .aggregateId("notif-456")
                .eventType("NotificationCreatedEvent")
                .payload("{}")
                .build();

            // Assert
            assertThat(entity).isNotNull();
            assertThat(entity.getAggregateType()).isEqualTo("Notification");
            assertThat(entity.getAggregateId()).isEqualTo("notif-456");
        }
    }

    @Nested
    @DisplayName("Domain Conversion Tests")
    class DomainConversionTests {

        @Test
        @DisplayName("Should convert to domain OutboxEvent")
        void testConvertToDomain() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(id)
                .aggregateType("Notification")
                .aggregateId("notif-789")
                .eventType("NotificationSentEvent")
                .payload("{\"sent\":true}")
                .createdAt(now)
                .status(OutboxStatus.PUBLISHED)
                .retryCount(1)
                .topic("notification.sent")
                .build();

            // Act
            OutboxEvent domainEvent = entity.toDomain();

            // Assert
            assertThat(domainEvent).isNotNull();
            assertThat(domainEvent.getId()).isEqualTo(id);
            assertThat(domainEvent.getAggregateType()).isEqualTo("Notification");
            assertThat(domainEvent.getAggregateId()).isEqualTo("notif-789");
            assertThat(domainEvent.getEventType()).isEqualTo("NotificationSentEvent");
            assertThat(domainEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        }

        @Test
        @DisplayName("Should convert from domain OutboxEvent")
        void testConvertFromDomain() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            OutboxEvent domainEvent = OutboxEvent.builder()
                .id(id)
                .aggregateType("Notification")
                .aggregateId("notif-999")
                .eventType("NotificationCreatedEvent")
                .payload("{\"created\":true}")
                .createdAt(now)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .topic("notification.created")
                .build();

            // Act
            OutboxEventEntity entity = OutboxEventEntity.fromDomain(domainEvent);

            // Assert
            assertThat(entity).isNotNull();
            assertThat(entity.getId()).isEqualTo(id);
            assertThat(entity.getAggregateType()).isEqualTo("Notification");
            assertThat(entity.getAggregateId()).isEqualTo("notif-999");
            assertThat(entity.getEventType()).isEqualTo("NotificationCreatedEvent");
            assertThat(entity.getStatus()).isEqualTo(OutboxStatus.PENDING);
        }

        @Test
        @DisplayName("Should support round-trip conversion")
        void testRoundTripConversion() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            OutboxEvent originalDomain = OutboxEvent.builder()
                .id(id)
                .aggregateType("Notification")
                .aggregateId("notif-roundtrip")
                .eventType("NotificationUpdatedEvent")
                .payload("{\"updated\":true}")
                .createdAt(now)
                .publishedAt(now.plusSeconds(10))
                .status(OutboxStatus.PUBLISHED)
                .retryCount(0)
                .errorMessage(null)
                .topic("notification.updated")
                .partitionKey("notif-roundtrip")
                .headers("{\"traceId\":\"trace-123\"}")
                .build();

            // Act
            OutboxEventEntity entity = OutboxEventEntity.fromDomain(originalDomain);
            OutboxEvent restoredDomain = entity.toDomain();

            // Assert
            assertThat(restoredDomain).isEqualTo(originalDomain);
        }
    }

    @Nested
    @DisplayName("Getters and Setters Tests")
    class GettersSettersTests {

        @Test
        @DisplayName("Should set and get all fields")
        void testSettersAndGetters() {
            // Arrange
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            OutboxEventEntity entity = new OutboxEventEntity();

            // Act
            entity.setId(id);
            entity.setAggregateType("Invoice");
            entity.setAggregateId("inv-001");
            entity.setEventType("InvoiceProcessedEvent");
            entity.setPayload("{\"amount\":100}");
            entity.setCreatedAt(now);
            entity.setPublishedAt(now.plusSeconds(5));
            entity.setStatus(OutboxStatus.PUBLISHED);
            entity.setRetryCount(0);
            entity.setErrorMessage(null);
            entity.setTopic("invoice.processed");
            entity.setPartitionKey("inv-001");
            entity.setHeaders("{\"key\":\"value\"}");

            // Assert
            assertThat(entity.getId()).isEqualTo(id);
            assertThat(entity.getAggregateType()).isEqualTo("Invoice");
            assertThat(entity.getAggregateId()).isEqualTo("inv-001");
            assertThat(entity.getEventType()).isEqualTo("InvoiceProcessedEvent");
            assertThat(entity.getPayload()).isEqualTo("{\"amount\":100}");
            assertThat(entity.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        }
    }
}
