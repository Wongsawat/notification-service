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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDispatcherService Tests")
class NotificationDispatcherServiceTest {

    @Mock
    private NotificationSendingService sendingService;

    private NotificationDispatcherService dispatcherService;

    private Notification testNotification;
    private UUID testId;

    @BeforeEach
    void setUp() {
        dispatcherService = new NotificationDispatcherService(sendingService);

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
    @DisplayName("dispatchAsync calls sendingService in async thread")
    void testDispatchAsync_callsSendingServiceInAsyncThread() {
        // Arrange
        when(sendingService.sendNotification(any(Notification.class)))
            .thenReturn(testNotification);

        // Act
        dispatcherService.dispatchAsync(testNotification);

        // Assert - wait for async execution
        await().atMost(java.time.Duration.ofSeconds(2))
            .untilAsserted(() -> verify(sendingService).sendNotification(testNotification));
    }

    @Test
    @DisplayName("dispatchAsync passes notification unchanged to sendingService")
    void testDispatchAsync_passesNotificationUnchanged() {
        // Arrange
        when(sendingService.sendNotification(any(Notification.class)))
            .thenReturn(testNotification);

        // Act
        dispatcherService.dispatchAsync(testNotification);

        // Assert - capture the argument passed to sendingService
        await().atMost(java.time.Duration.ofSeconds(2))
            .untilAsserted(() -> {
                ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
                verify(sendingService).sendNotification(captor.capture());
                assertThat(captor.getValue().getId()).isEqualTo(testId);
            });
    }
}
