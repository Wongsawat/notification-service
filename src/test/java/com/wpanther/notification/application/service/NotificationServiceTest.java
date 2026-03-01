package com.wpanther.notification.application.service;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.domain.repository.NotificationRepository;
import com.wpanther.notification.domain.service.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Application Service Tests")
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationSender emailSender;

    @Mock
    private NotificationSender webhookSender;

    @InjectMocks
    private NotificationService notificationService;

    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testNotification = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test Subject")
            .status(NotificationStatus.PENDING)
            .build();

        // Configure mock senders with lenient stubbing to avoid UnnecessaryStubbingException
        lenient().when(emailSender.supports(any())).thenAnswer(invocation -> {
            NotificationChannel channel = invocation.getArgument(0);
            return channel == NotificationChannel.EMAIL;
        });
        lenient().when(webhookSender.supports(any())).thenAnswer(invocation -> {
            NotificationChannel channel = invocation.getArgument(0);
            return channel == NotificationChannel.WEBHOOK;
        });

        // Set maxRetries via reflection
        ReflectionTestUtils.setField(notificationService, "maxRetries", 3);

        // Set senders list via reflection
        ReflectionTestUtils.setField(notificationService, "senders", List.of(emailSender, webhookSender));
    }

    // ========== Send Notification Tests ==========

    @Test
    @DisplayName("Should send notification successfully and update status to SENT")
    void testSendNotificationHappyPath() throws Exception {
        // Arrange
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(emailSender).send(any());

        // Act
        Notification result = notificationService.sendNotification(testNotification);

        // Assert
        verify(repository, times(3)).save(any(Notification.class));
        verify(emailSender).send(any(Notification.class));

        // Final result should be SENT with sentAt timestamp
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return FAILED notification without throwing when sender fails")
    void testSendNotification_returnsFailed_withoutThrowing() throws Exception {
        // Arrange
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("SMTP error")).when(emailSender).send(any());

        // Act - must NOT throw; FAILED status must be returned so @Transactional can commit
        Notification result = notificationService.sendNotification(testNotification);

        // Assert
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("SMTP error");
        assertThat(result.getFailedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return FAILED notification without throwing when no sender found")
    void testSendNotification_returnsFailed_whenNoSenderFound() throws Exception {
        // Arrange
        testNotification.setChannel(NotificationChannel.SMS);
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act - must NOT throw so @Transactional can commit the FAILED status
        Notification result = notificationService.sendNotification(testNotification);

        // Assert
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("No sender found for channel");
        assertThat(result.getFailedAt()).isNotNull();
    }

    // ========== Sender Selection Tests ==========

    @Test
    @DisplayName("Should select EMAIL sender for EMAIL channel")
    void testFindSenderForEmail() throws Exception {
        // Arrange
        testNotification.setChannel(NotificationChannel.EMAIL);
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(emailSender).send(any());

        // Act
        notificationService.sendNotification(testNotification);

        // Assert
        verify(emailSender).send(any(Notification.class));
        verify(webhookSender, never()).send(any());
    }

    @Test
    @DisplayName("Should select WEBHOOK sender for WEBHOOK channel")
    void testFindSenderForWebhook() throws Exception {
        // Arrange
        testNotification.setChannel(NotificationChannel.WEBHOOK);
        testNotification.setRecipient("https://api.example.com/webhook");
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(webhookSender).send(any());

        // Act
        notificationService.sendNotification(testNotification);

        // Assert
        verify(webhookSender).send(any(Notification.class));
        verify(emailSender, never()).send(any());
    }

    // ========== Create and Send Tests ==========

    @Test
    @DisplayName("Should create notification from template and send")
    void testCreateAndSend() throws Exception {
        // Arrange
        Map<String, Object> templateVars = Map.of("invoiceNumber", "INV-001");
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(emailSender).send(any());

        // Act
        Notification result = notificationService.createAndSend(
            NotificationType.INVOICE_PROCESSED,
            NotificationChannel.EMAIL,
            "test@example.com",
            "invoice-processed",
            templateVars
        );

        // Assert
        assertThat(result.getType()).isEqualTo(NotificationType.INVOICE_PROCESSED);
        assertThat(result.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(result.getRecipient()).isEqualTo("test@example.com");
        assertThat(result.getTemplateName()).isEqualTo("invoice-processed");
        assertThat(result.getTemplateVariables()).containsAllEntriesOf(templateVars);
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);

        verify(emailSender).send(any(Notification.class));
    }

    // ========== Retry Failed Notifications Tests ==========

    @Test
    @DisplayName("Should retry failed notifications with retryCount < maxRetries")
    void testRetryFailedNotificationsRetries() {
        // Arrange
        Notification failedNotification = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.FAILED)
            .retryCount(1)
            .build();

        when(repository.findFailedNotifications(3)).thenReturn(List.of(failedNotification));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        notificationService.retryFailedNotifications();

        // Assert
        // Verify that save was called at least once (prepareRetry saves with RETRYING status)
        verify(repository, atLeastOnce()).save(any(Notification.class));

        // The retry count should be incremented
        assertThat(failedNotification.getRetryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should not retry when no failed notifications found")
    void testRetryFailedNotificationsWhenNoneFound() throws Exception {
        // Arrange
        when(repository.findFailedNotifications(3)).thenReturn(List.of());

        // Act
        notificationService.retryFailedNotifications();

        // Assert
        verify(repository, never()).save(any());
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("Should handle exception during retry gracefully")
    void testRetryFailedNotificationsHandlesException() {
        // Arrange
        Notification failedNotification = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.FAILED)
            .retryCount(1)
            .build();

        when(repository.findFailedNotifications(3)).thenReturn(List.of(failedNotification));
        when(repository.save(any(Notification.class))).thenThrow(new RuntimeException("Database error"));

        // Act - should not throw exception
        notificationService.retryFailedNotifications();

        // Assert
        verify(repository).save(any(Notification.class));
    }

    // ========== Process Pending Notifications Tests ==========

    @Test
    @DisplayName("Should process pending notifications")
    void testProcessPendingNotifications() {
        // Arrange
        Notification pendingNotification = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.PENDING)
            .retryCount(0)
            .build();

        when(repository.findPendingNotifications()).thenReturn(List.of(pendingNotification));

        // Act
        notificationService.processPendingNotifications();

        // Assert
        verify(repository).findPendingNotifications();
    }

    @Test
    @DisplayName("Should not process when no pending notifications found")
    void testProcessPendingNotificationsWhenNoneFound() {
        // Arrange
        when(repository.findPendingNotifications()).thenReturn(List.of());

        // Act
        notificationService.processPendingNotifications();

        // Assert
        verify(repository).findPendingNotifications();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle exception during processing gracefully")
    void testProcessPendingNotificationsHandlesException() {
        // Arrange
        Notification pendingNotification = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.PENDING)
            .retryCount(0)
            .build();

        when(repository.findPendingNotifications()).thenReturn(List.of(pendingNotification));
        when(repository.save(any(Notification.class))).thenThrow(new RuntimeException("Database error"));

        // Act - should not throw exception
        notificationService.processPendingNotifications();

        // Assert
        verify(repository).findPendingNotifications();
    }

    // ========== Statistics Tests ==========

    @Test
    @DisplayName("Should return notification statistics by status")
    void testGetStatistics() {
        // Arrange
        when(repository.countByStatus(NotificationStatus.PENDING)).thenReturn(5L);
        when(repository.countByStatus(NotificationStatus.SENDING)).thenReturn(2L);
        when(repository.countByStatus(NotificationStatus.SENT)).thenReturn(100L);
        when(repository.countByStatus(NotificationStatus.FAILED)).thenReturn(3L);
        when(repository.countByStatus(NotificationStatus.RETRYING)).thenReturn(1L);

        // Act
        Map<String, Long> statistics = notificationService.getStatistics();

        // Assert
        assertThat(statistics)
            .containsEntry("pending", 5L)
            .containsEntry("sending", 2L)
            .containsEntry("sent", 100L)
            .containsEntry("failed", 3L)
            .containsEntry("retrying", 1L);

        verify(repository, times(5)).countByStatus(any(NotificationStatus.class));
    }

    @Test
    @DisplayName("Should return zero counts when no notifications exist")
    void testGetStatisticsWhenEmpty() {
        // Arrange
        when(repository.countByStatus(any(NotificationStatus.class))).thenReturn(0L);

        // Act
        Map<String, Long> statistics = notificationService.getStatistics();

        // Assert
        assertThat(statistics).allSatisfy((key, value) -> assertThat(value).isZero());
    }
}
