package com.wpanther.notification.application.service;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.domain.repository.NotificationRepository;
import com.wpanther.notification.application.port.out.NotificationSenderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationSendingService Tests")
class NotificationSendingServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationSenderPort emailSender;

    @Mock
    private NotificationSenderPort webhookSender;

    @Mock
    private PlatformTransactionManager txManager;

    @Mock
    private TransactionStatus txStatus;

    private NotificationSendingService sendingService;

    private Notification testNotification;

    @BeforeEach
    void setUp() {
        // Make TransactionTemplate.execute() actually invoke the callback
        lenient().when(txManager.getTransaction(any())).thenReturn(txStatus);

        sendingService = new NotificationSendingService(repository, List.of(emailSender, webhookSender), txManager);
        ReflectionTestUtils.setField(sendingService, "maxRetries", 3);

        testNotification = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test Subject")
            .status(NotificationStatus.PENDING)
            .build();

        lenient().when(emailSender.supports(any())).thenAnswer(inv ->
            inv.getArgument(0) == NotificationChannel.EMAIL);
        lenient().when(webhookSender.supports(any())).thenAnswer(inv ->
            inv.getArgument(0) == NotificationChannel.WEBHOOK);
    }

    // ── sendNotification Tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should send notification successfully and update status to SENT")
    void testSendNotificationHappyPath() throws Exception {
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailSender).send(any());

        Notification result = sendingService.sendNotification(testNotification);

        verify(repository, times(3)).save(any(Notification.class));
        verify(emailSender).send(any(Notification.class));
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return FAILED notification without throwing when sender fails")
    void testSendNotification_returnsFailed_withoutThrowing() throws Exception {
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("SMTP error")).when(emailSender).send(any());

        Notification result = sendingService.sendNotification(testNotification);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("SMTP error");
        assertThat(result.getFailedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return FAILED when no sender found for channel")
    void testSendNotification_returnsFailed_whenNoSenderFound() throws Exception {
        testNotification = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.SMS)
            .recipient("sms-recipient")
            .subject("Test Subject")
            .status(NotificationStatus.PENDING)
            .build();
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        Notification result = sendingService.sendNotification(testNotification);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("No sender found for channel");
        assertThat(result.getFailedAt()).isNotNull();
    }

    // ── Sender selection Tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should select EMAIL sender for EMAIL channel")
    void testFindSenderForEmail() throws Exception {
        // testNotification is already EMAIL channel (set in setUp)
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailSender).send(any());

        sendingService.sendNotification(testNotification);

        verify(emailSender).send(any(Notification.class));
        verify(webhookSender, never()).send(any());
    }

    @Test
    @DisplayName("Should select WEBHOOK sender for WEBHOOK channel")
    void testFindSenderForWebhook() throws Exception {
        testNotification = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.WEBHOOK)
            .recipient("https://api.example.com/webhook")
            .subject("Test Subject")
            .status(NotificationStatus.PENDING)
            .build();
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(webhookSender).send(any());

        sendingService.sendNotification(testNotification);

        verify(webhookSender).send(any(Notification.class));
        verify(emailSender, never()).send(any());
    }

    // ── createAndSend Tests ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should create notification from template and send")
    void testCreateAndSend() throws Exception {
        Map<String, Object> templateVars = Map.of("documentNumber", "INV-001");
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailSender).send(any());

        Notification result = sendingService.createAndSend(
            NotificationType.INVOICE_PROCESSED,
            NotificationChannel.EMAIL,
            "test@example.com",
            "invoice-processed",
            templateVars
        );

        assertThat(result.getType()).isEqualTo(NotificationType.INVOICE_PROCESSED);
        assertThat(result.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(result.getRecipient()).isEqualTo("test@example.com");
        assertThat(result.getTemplateName()).isEqualTo("invoice-processed");
        assertThat(result.getTemplateVariables()).containsAllEntriesOf(templateVars);
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);

        verify(emailSender).send(any(Notification.class));
    }

    // ── getStatistics Tests ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return notification statistics by status")
    void testGetStatistics() {
        when(repository.countByStatus(NotificationStatus.PENDING)).thenReturn(5L);
        when(repository.countByStatus(NotificationStatus.SENDING)).thenReturn(2L);
        when(repository.countByStatus(NotificationStatus.SENT)).thenReturn(100L);
        when(repository.countByStatus(NotificationStatus.FAILED)).thenReturn(3L);
        when(repository.countByStatus(NotificationStatus.RETRYING)).thenReturn(1L);

        Map<String, Long> statistics = sendingService.getStatistics();

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
        when(repository.countByStatus(any(NotificationStatus.class))).thenReturn(0L);

        Map<String, Long> statistics = sendingService.getStatistics();

        assertThat(statistics).allSatisfy((key, value) -> assertThat(value).isZero());
    }
}
