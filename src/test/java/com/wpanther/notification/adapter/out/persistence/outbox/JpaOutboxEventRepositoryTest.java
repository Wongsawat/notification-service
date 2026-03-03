package com.wpanther.notification.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaOutboxEventRepository.class)
@ActiveProfiles("test")
@DisplayName("JpaOutboxEventRepository Tests")
class JpaOutboxEventRepositoryTest {

    @Autowired
    private JpaOutboxEventRepository repository;

    @Autowired
    private SpringDataOutboxRepository springRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Nested
    @DisplayName("save() tests")
    class SaveTests {

        @Test
        @DisplayName("Should save outbox event")
        void testSaveOutboxEvent() {
            // Arrange
            OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Notification")
                .aggregateId("notif-123")
                .eventType("NotificationSentEvent")
                .payload("{\"sent\":true}")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();

            // Act
            OutboxEvent saved = repository.save(event);

            // Assert
            assertThat(saved).isNotNull();
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getAggregateType()).isEqualTo("Notification");
            assertThat(saved.getAggregateId()).isEqualTo("notif-123");
            assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        }

        @Test
        @DisplayName("Should update existing outbox event")
        void testUpdateExistingOutboxEvent() {
            // Arrange - save an event first
            OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Notification")
                .aggregateId("notif-456")
                .eventType("NotificationCreatedEvent")
                .payload("{\"created\":true}")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .build();

            OutboxEvent saved = repository.save(event);

            // Act - update the event
            OutboxEvent updated = OutboxEvent.builder()
                .id(saved.getId())
                .aggregateType(saved.getAggregateType())
                .aggregateId(saved.getAggregateId())
                .eventType(saved.getEventType())
                .payload(saved.getPayload())
                .createdAt(saved.getCreatedAt())
                .status(OutboxStatus.PUBLISHED)
                .publishedAt(Instant.now())
                .build();

            OutboxEvent result = repository.save(updated);

            // Assert
            assertThat(result.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(result.getPublishedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findById() tests")
    class FindByIdTests {

        @Test
        @DisplayName("Should find event by ID")
        void testFindById() {
            // Arrange
            OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Notification")
                .aggregateId("notif-789")
                .eventType("NotificationSentEvent")
                .payload("{\"sent\":true}")
                .status(OutboxStatus.PUBLISHED)
                .createdAt(Instant.now())
                .build();

            OutboxEvent saved = repository.save(event);

            // Act
            Optional<OutboxEvent> found = repository.findById(saved.getId());

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getAggregateId()).isEqualTo("notif-789");
        }

        @Test
        @DisplayName("Should return empty when event not found")
        void testFindByIdNotFound() {
            // Act
            Optional<OutboxEvent> found = repository.findById(UUID.randomUUID());

            // Assert
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findPendingEvents() tests")
    class FindPendingEventsTests {

        @Test
        @DisplayName("Should find pending events ordered by creation time")
        void testFindPendingEvents() {
            // Arrange - create multiple events with different statuses
            Instant now = Instant.now();

            repository.save(OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Notification")
                .aggregateId("notif-pending-1")
                .eventType("Event1")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .createdAt(now.minusSeconds(10))
                .build());

            repository.save(OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Notification")
                .aggregateId("notif-pending-2")
                .eventType("Event2")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .createdAt(now.minusSeconds(5))
                .build());

            repository.save(OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Notification")
                .aggregateId("notif-published")
                .eventType("Event3")
                .payload("{}")
                .status(OutboxStatus.PUBLISHED)
                .createdAt(now.minusSeconds(3))
                .build());

            // Act
            List<OutboxEvent> pendingEvents = repository.findPendingEvents(10);

            // Assert
            assertThat(pendingEvents).hasSize(2);
            assertThat(pendingEvents.get(0).getAggregateId()).isEqualTo("notif-pending-1");
            assertThat(pendingEvents.get(1).getAggregateId()).isEqualTo("notif-pending-2");
        }

        @Test
        @DisplayName("Should limit results to specified limit")
        void testFindPendingEventsWithLimit() {
            // Arrange
            for (int i = 0; i < 5; i++) {
                repository.save(OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateType("Notification")
                    .aggregateId("notif-pending-" + i)
                    .eventType("Event" + i)
                    .payload("{}")
                    .status(OutboxStatus.PENDING)
                    .createdAt(Instant.now())
                    .build());
            }

            // Act
            List<OutboxEvent> pendingEvents = repository.findPendingEvents(3);

            // Assert
            assertThat(pendingEvents).hasSize(3);
        }
    }
}
