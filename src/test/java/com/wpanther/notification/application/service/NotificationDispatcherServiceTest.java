package com.wpanther.notification.application.service;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatcherService Tests")
class NotificationDispatcherServiceTest {

    @Mock
    private NotificationService notificationService;

    private NotificationDispatcherService dispatcherService;

    private Notification testNotification;
    private UUID testId;

    @BeforeEach
    void setUp() {
        dispatcherService = new NotificationDispatcherService(notificationService);

        testId = UUID.randomUUID();
        testNotification = Notification.builder()
            .id(testId)
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.PENDING)
            .build();
    }

    @Test
    @DisplayName("dispatchAsync calls notificationService in async thread")
    void testDispatchAsync_callsNotificationServiceInAsyncThread() {
        // Arrange
        when(notificationService.sendNotification(any(Notification.class)))
            .thenReturn(testNotification);

        // Act
        dispatcherService.dispatchAsync(testNotification);

        // Assert - wait for async execution
        await().atMost(java.time.Duration.ofSeconds(2))
            .untilAsserted(() -> verify(notificationService).sendNotification(testNotification));
    }

    @Test
    @DisplayName("dispatchAsync passes notification unchanged to service")
    void testDispatchAsync_passesNotificationUnchanged() {
        // Arrange
        when(notificationService.sendNotification(any(Notification.class)))
            .thenReturn(testNotification);

        // Act
        dispatcherService.dispatchAsync(testNotification);

        // Assert - capture the argument passed to notificationService
        await().atMost(java.time.Duration.ofSeconds(2))
            .untilAsserted(() -> {
                ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
                verify(notificationService).sendNotification(captor.capture());
                // Verify it's the same notification (same ID)
                assert captor.getValue().getId().equals(testId);
            });
    }
}
