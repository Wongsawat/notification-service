package com.wpanther.notification.application.service;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.application.port.out.NotificationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Application Service Tests")
class NotificationServiceTest {

    @Mock
    private NotificationRepositoryPort repository;

    @Mock
    private NotificationSendingService sendingService;

    @Mock
    private NotificationDispatcherService dispatcherService;

    @InjectMocks
    private NotificationService notificationService;

    private Notification testNotification;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testNotification = Notification.builder()
            .id(testId)
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test Subject")
            .status(NotificationStatus.PENDING)
            .build();

        ReflectionTestUtils.setField(notificationService, "maxRetries", 3);
        ReflectionTestUtils.setField(notificationService, "staleSendingTimeoutMs", 300000L);
    }

    // ── Delegation tests ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendNotification delegates to NotificationSendingService")
    void testSendNotification_delegatesToSendingService() {
        when(sendingService.sendNotification(testNotification)).thenReturn(testNotification);

        Notification result = notificationService.sendNotification(testNotification);

        verify(sendingService).sendNotification(testNotification);
        assertThat(result).isEqualTo(testNotification);
    }

    @Test
    @DisplayName("getStatistics delegates to NotificationSendingService")
    void testGetStatistics_delegatesToSendingService() {
        Map<String, Long> stats = Map.of("sent", 10L, "failed", 2L);
        when(sendingService.getStatistics()).thenReturn(stats);

        Map<String, Long> result = notificationService.getStatistics();

        verify(sendingService).getStatistics();
        assertThat(result).isEqualTo(stats);
    }

    // ── Query method tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById delegates to repository")
    void testFindById_delegatesToRepository() {
        when(repository.findById(testId)).thenReturn(Optional.of(testNotification));

        Optional<Notification> result = notificationService.findById(testId);

        verify(repository).findById(testId);
        assertThat(result).contains(testNotification);
    }

    @Test
    @DisplayName("findById returns empty when notification not found")
    void testFindById_returnsEmpty_whenNotFound() {
        when(repository.findById(testId)).thenReturn(Optional.empty());

        Optional<Notification> result = notificationService.findById(testId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByInvoiceId delegates to repository")
    void testFindByInvoiceId_delegatesToRepository() {
        when(repository.findByInvoiceId("INV-001")).thenReturn(List.of(testNotification));

        List<Notification> result = notificationService.findByInvoiceId("INV-001");

        verify(repository).findByInvoiceId("INV-001");
        assertThat(result).containsExactly(testNotification);
    }

    @Test
    @DisplayName("findByStatus delegates to repository")
    void testFindByStatus_delegatesToRepository() {
        when(repository.findByStatus(NotificationStatus.SENT)).thenReturn(List.of(testNotification));

        List<Notification> result = notificationService.findByStatus(NotificationStatus.SENT);

        verify(repository).findByStatus(NotificationStatus.SENT);
        assertThat(result).containsExactly(testNotification);
    }

    // ── prepareAndDispatchRetry tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("prepareAndDispatchRetry succeeds for retryable notification")
    void testPrepareAndDispatchRetry_success() {
        Notification failedNotification = Notification.builder()
            .id(testId)
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.FAILED)
            .retryCount(1)
            .build();

        when(repository.findById(testId)).thenReturn(Optional.of(failedNotification));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.prepareAndDispatchRetry(testId);

        verify(repository).findById(testId);
        verify(repository).save(failedNotification);
        verify(dispatcherService).dispatchAsync(failedNotification);
        assertThat(failedNotification.getRetryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("prepareAndDispatchRetry throws NoSuchElementException when not found")
    void testPrepareAndDispatchRetry_notFound() {
        when(repository.findById(testId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.prepareAndDispatchRetry(testId))
            .isInstanceOf(NoSuchElementException.class);

        verify(dispatcherService, never()).dispatchAsync(any());
    }

    @Test
    @DisplayName("prepareAndDispatchRetry throws IllegalStateException when max retries reached")
    void testPrepareAndDispatchRetry_maxRetriesReached() {
        Notification maxRetriesNotification = Notification.builder()
            .id(testId)
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.FAILED)
            .retryCount(3) // equals maxRetries
            .build();

        when(repository.findById(testId)).thenReturn(Optional.of(maxRetriesNotification));

        assertThatThrownBy(() -> notificationService.prepareAndDispatchRetry(testId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot retry notification");

        verify(dispatcherService, never()).dispatchAsync(any());
    }

    // ── Scheduler: retryFailedNotifications tests ─────────────────────────────────────────

    @Test
    @DisplayName("retryFailedNotifications retries notifications with retryCount < maxRetries")
    void testRetryFailedNotificationsRetries() {
        Notification failedNotification = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.FAILED)
            .retryCount(1)
            .build();

        when(repository.findFailedNotifications(3, 100)).thenReturn(List.of(failedNotification));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.retryFailedNotifications();

        verify(repository, atLeastOnce()).save(any(Notification.class));
        assertThat(failedNotification.getRetryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("retryFailedNotifications does nothing when no failed notifications")
    void testRetryFailedNotificationsWhenNoneFound() {
        when(repository.findFailedNotifications(3, 100)).thenReturn(List.of());

        notificationService.retryFailedNotifications();

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("retryFailedNotifications handles exception gracefully")
    void testRetryFailedNotificationsHandlesException() {
        Notification failedNotification = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.FAILED)
            .retryCount(1)
            .build();

        when(repository.findFailedNotifications(3, 100)).thenReturn(List.of(failedNotification));
        when(repository.save(any(Notification.class))).thenThrow(new RuntimeException("Database error"));

        notificationService.retryFailedNotifications(); // must not throw

        verify(repository).save(any(Notification.class));
    }

    // ── Scheduler: processPendingNotifications tests ──────────────────────────────────────

    @Test
    @DisplayName("processPendingNotifications dispatches each pending notification")
    void testProcessPendingNotifications() {
        Notification pendingNotification = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.PENDING)
            .retryCount(0)
            .build();

        when(repository.findPendingNotifications(100)).thenReturn(List.of(pendingNotification));

        notificationService.processPendingNotifications();

        verify(repository).findPendingNotifications(100);
        verify(dispatcherService).dispatchAsync(pendingNotification);
    }

    @Test
    @DisplayName("processPendingNotifications does nothing when no pending notifications")
    void testProcessPendingNotificationsWhenNoneFound() {
        when(repository.findPendingNotifications(100)).thenReturn(List.of());

        notificationService.processPendingNotifications();

        verify(repository).findPendingNotifications(100);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("processPendingNotifications handles dispatch exception gracefully")
    void testProcessPendingNotificationsHandlesException() {
        Notification pendingNotification = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.PENDING)
            .retryCount(0)
            .build();

        when(repository.findPendingNotifications(100)).thenReturn(List.of(pendingNotification));
        doThrow(new RuntimeException("Dispatch error")).when(dispatcherService).dispatchAsync(any());

        notificationService.processPendingNotifications(); // must not throw

        verify(dispatcherService).dispatchAsync(pendingNotification);
    }

    // ── Scheduler: recoverStaleSendingNotifications tests ────────────────────────────────

    @Test
    @DisplayName("recoverStaleSendingNotifications marks stale SENDING notifications as FAILED")
    void testRecoverStaleSendingNotifications() {
        Notification staleSending = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.SENDING)
            .retryCount(0)
            .build();

        when(repository.findStaleSendingNotifications(any(LocalDateTime.class), eq(100)))
            .thenReturn(List.of(staleSending));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.recoverStaleSendingNotifications();

        verify(repository).save(staleSending);
        assertThat(staleSending.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(staleSending.getErrorMessage()).contains("stale SENDING");
    }

    @Test
    @DisplayName("recoverStaleSendingNotifications does nothing when no stale notifications")
    void testRecoverStaleSendingNotificationsWhenNone() {
        when(repository.findStaleSendingNotifications(any(LocalDateTime.class), eq(100)))
            .thenReturn(List.of());

        notificationService.recoverStaleSendingNotifications();

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("recoverStaleSendingNotifications handles per-notification exception gracefully")
    void testRecoverStaleSendingNotificationsHandlesException() {
        Notification staleSending = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test")
            .status(NotificationStatus.SENDING)
            .retryCount(0)
            .build();

        when(repository.findStaleSendingNotifications(any(LocalDateTime.class), eq(100)))
            .thenReturn(List.of(staleSending));
        when(repository.save(any(Notification.class))).thenThrow(new RuntimeException("DB error"));

        notificationService.recoverStaleSendingNotifications(); // must not throw

        verify(repository).save(any(Notification.class));
    }
}
