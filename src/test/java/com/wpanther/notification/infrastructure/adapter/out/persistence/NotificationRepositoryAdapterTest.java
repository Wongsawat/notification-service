package com.wpanther.notification.infrastructure.adapter.out.persistence;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
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
@Import(NotificationRepositoryAdapter.class)
@ActiveProfiles("test")
@DisplayName("NotificationRepositoryAdapter Tests")
class NotificationRepositoryAdapterTest {

    @Autowired
    private NotificationRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    @Nested
    @DisplayName("save() and findById() tests")
    class SaveAndFindTests {

        @Test
        @DisplayName("Should save and find notification by ID")
        void testSaveAndFindById() {
            // Arrange
            Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .recipient("test@example.com")
                .subject("Test Subject")
                .body("Test Body")
                .metadata(java.util.Map.of("key", "value"))
                .templateVariables(java.util.Map.of())
                .createdAt(Instant.now())
                .retryCount(0)
                .build();

            // Act
            Notification saved = adapter.save(notification);
            Optional<Notification> found = adapter.findById(saved.getId());

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getRecipient()).isEqualTo("test@example.com");
            assertThat(found.get().getSubject()).isEqualTo("Test Subject");
        }

        @Test
        @DisplayName("Should return empty when notification not found")
        void testFindByIdNotFound() {
            // Act
            Optional<Notification> found = adapter.findById(UUID.randomUUID());

            // Assert
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("Should update existing notification")
        void testUpdateExistingNotification() {
            // Arrange
            Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.PENDING)
                .recipient("test@example.com")
                .subject("Original Subject")
                .body("Original Body")
                .metadata(java.util.Map.of())
                .templateVariables(java.util.Map.of())
                .createdAt(Instant.now())
                .retryCount(0)
                .build();

            Notification saved = adapter.save(notification);

            // Act - update the notification via state machine and permitted setter
            saved.setSubject("Updated Subject");
            saved.markSending();
            saved.markSent(); // sets sentAt automatically

            Notification updated = adapter.save(saved);
            Optional<Notification> found = adapter.findById(updated.getId());

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getSubject()).isEqualTo("Updated Subject");
            assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(found.get().getSentAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query tests")
    class QueryTests {

        @Test
        @DisplayName("Should find notifications by status")
        void testFindByStatus() {
            // Arrange
            adapter.save(createNotification(NotificationStatus.PENDING));
            adapter.save(createNotification(NotificationStatus.SENT));
            adapter.save(createNotification(NotificationStatus.PENDING));

            // Act
            List<Notification> pendingNotifications = adapter.findByStatus(NotificationStatus.PENDING, 100);
            List<Notification> sentNotifications = adapter.findByStatus(NotificationStatus.SENT, 100);

            // Assert
            assertThat(pendingNotifications).hasSize(2);
            assertThat(sentNotifications).hasSize(1);
        }

        @Test
        @DisplayName("Should find notifications by document ID")
        void testFindByDocumentId() {
            // Arrange
            Notification notif1 = createNotification();
            notif1.setDocumentId("INV-001");
            adapter.save(notif1);

            Notification notif2 = createNotification();
            notif2.setDocumentId("INV-002");
            adapter.save(notif2);

            Notification notif3 = createNotification();
            notif3.setDocumentId("INV-001");
            adapter.save(notif3);

            // Act
            List<Notification> doc001Notifications = adapter.findByDocumentId("INV-001", 100);

            // Assert
            assertThat(doc001Notifications).hasSize(2);
        }

        @Test
        @DisplayName("Should find notifications by document number")
        void testFindByDocumentNumber() {
            // Arrange
            Notification notif1 = createNotification();
            notif1.setDocumentNumber("INV-2024-001");
            adapter.save(notif1);

            Notification notif2 = createNotification();
            notif2.setDocumentNumber("INV-2024-002");
            adapter.save(notif2);

            // Act
            List<Notification> notifications = adapter.findByDocumentNumber("INV-2024-001", 100);

            // Assert
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getDocumentNumber()).isEqualTo("INV-2024-001");
        }

        @Test
        @DisplayName("Should find stale SENDING notifications older than threshold")
        void testFindStaleSendingNotifications() {
            // Arrange - a notification that has been stuck in SENDING for a long time
            Notification stale = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.SENDING)
                .recipient("test@example.com")
                .subject("Test")
                .body("Body")
                .metadata(new java.util.HashMap<>())
                .templateVariables(new java.util.HashMap<>())
                .createdAt(Instant.now().minusSeconds(600))
                .retryCount(0)
                .build();
            adapter.save(stale);

            // A recent SENDING notification — should NOT be returned
            Notification recent = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.SENDING)
                .recipient("test@example.com")
                .subject("Recent")
                .body("Body")
                .metadata(new java.util.HashMap<>())
                .templateVariables(new java.util.HashMap<>())
                .createdAt(Instant.now())
                .retryCount(0)
                .build();
            adapter.save(recent);

            // Act — threshold is 5 minutes ago
            Instant threshold = Instant.now().minusSeconds(300);
            List<Notification> staleFound = adapter.findStaleSendingNotifications(threshold, 100);

            // Assert
            assertThat(staleFound).hasSize(1);
            assertThat(staleFound.get(0).getStatus()).isEqualTo(NotificationStatus.SENDING);
        }

        @Test
        @DisplayName("Should find failed notifications")
        void testFindFailedNotifications() {
            // Arrange
            adapter.save(createNotification(NotificationStatus.FAILED, 1));
            adapter.save(createNotification(NotificationStatus.FAILED, 2));
            adapter.save(createNotification(NotificationStatus.FAILED, 5)); // Above max

            // Act
            List<Notification> failed = adapter.findFailedNotifications(3, 100);

            // Assert
            assertThat(failed).hasSize(2);
        }

        @Test
        @DisplayName("Should find pending notifications")
        void testFindPendingNotifications() {
            // Arrange
            adapter.save(createNotification(NotificationStatus.PENDING));
            adapter.save(createNotification(NotificationStatus.SENT));
            adapter.save(createNotification(NotificationStatus.PENDING));

            // Act
            List<Notification> pending = adapter.findPendingNotifications(100);

            // Assert
            assertThat(pending).hasSize(2);
        }

        @Test
        @DisplayName("Should count notifications by status")
        void testCountByStatus() {
            // Arrange
            adapter.save(createNotification(NotificationStatus.PENDING));
            adapter.save(createNotification(NotificationStatus.PENDING));
            adapter.save(createNotification(NotificationStatus.SENT));

            // Act
            long pendingCount = adapter.countByStatus(NotificationStatus.PENDING);
            long sentCount = adapter.countByStatus(NotificationStatus.SENT);

            // Assert
            assertThat(pendingCount).isEqualTo(2);
            assertThat(sentCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Delete tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete notification by ID")
        void testDeleteById() {
            // Arrange
            Notification saved = adapter.save(createNotification());
            UUID id = saved.getId();

            // Act
            adapter.deleteById(id);
            Optional<Notification> found = adapter.findById(id);

            // Assert
            assertThat(found).isEmpty();
        }
    }

    private Notification createNotification() {
        return createNotification(NotificationStatus.PENDING);
    }

    private Notification createNotification(NotificationStatus status) {
        return createNotification(status, 0);
    }

    private Notification createNotification(NotificationStatus status, int retryCount) {
        return Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .status(status)
            .recipient("test@example.com")
            .subject("Test")
            .body("Body")
            .metadata(new java.util.HashMap<>())
            .templateVariables(new java.util.HashMap<>())
            .createdAt(Instant.now())
            .retryCount(retryCount)
            .build();
    }
}
