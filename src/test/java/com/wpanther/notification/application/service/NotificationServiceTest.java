package com.wpanther.notification.application.service;

import com.wpanther.notification.application.port.in.event.InvoicePdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.TaxInvoicePdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.DocumentReceivedTraceEvent;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.domain.model.DocumentIntakeStat;
import com.wpanther.notification.domain.repository.NotificationRepository;
import com.wpanther.notification.domain.repository.DocumentIntakeStatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
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
    private NotificationRepository repository;

    @Mock
    private NotificationSendingService sendingService;

    @Mock
    private NotificationDispatcherService dispatcherService;

    @Mock
    private DocumentIntakeStatRepository documentIntakeStatRepository;

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
    @DisplayName("findByDocumentId delegates to repository with limit")
    void testFindByDocumentId_delegatesToRepository() {
        when(repository.findByDocumentId("INV-001", 50)).thenReturn(List.of(testNotification));

        List<Notification> result = notificationService.findByDocumentId("INV-001", 50);

        verify(repository).findByDocumentId("INV-001", 50);
        assertThat(result).containsExactly(testNotification);
    }

    @Test
    @DisplayName("findByStatus delegates to repository with limit")
    void testFindByStatus_delegatesToRepository() {
        when(repository.findByStatus(NotificationStatus.SENT, 50)).thenReturn(List.of(testNotification));

        List<Notification> result = notificationService.findByStatus(NotificationStatus.SENT, 50);

        verify(repository).findByStatus(NotificationStatus.SENT, 50);
        assertThat(result).containsExactly(testNotification);
    }

    // ── ProcessingEventUseCase: handleInvoicePdfGenerated (pdf.generated.invoice) ─────────

    @Test
    @DisplayName("handleInvoicePdfGenerated creates PDF_GENERATED notification and dispatches async")
    void testHandleInvoicePdfGenerated_dispatchesAsync() {
        InvoicePdfGeneratedEvent event = new InvoicePdfGeneratedEvent(
            "INV-2025-001", "doc-001", "http://example.com/doc", 102400L, true, false, "corr-1");

        ReflectionTestUtils.setField(notificationService, "defaultRecipient", "admin@example.com");

        notificationService.handleInvoicePdfGenerated(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(dispatcherService).dispatchAsync(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.getType()).isEqualTo(NotificationType.PDF_GENERATED);
        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(notification.getTemplateName()).isEqualTo("pdf-generated");
    }

    // ── ProcessingEventUseCase: handleTaxInvoicePdfGenerated ─────────────────────────────

    @Test
    @DisplayName("handleTaxInvoicePdfGenerated creates TAX_INVOICE_PDF_GENERATED notification and dispatches async")
    void testHandleTaxInvoicePdfGenerated_dispatchesAsync() {
        TaxInvoicePdfGeneratedEvent event = new TaxInvoicePdfGeneratedEvent(
            "saga-1", "doc-001", "TAXINV-2025-001", "http://example.com/taxdoc", 204800L, true, "corr-1");

        ReflectionTestUtils.setField(notificationService, "defaultRecipient", "admin@example.com");

        notificationService.handleTaxInvoicePdfGenerated(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(dispatcherService).dispatchAsync(captor.capture());
        Notification notification = captor.getValue();
        assertThat(notification.getType()).isEqualTo(NotificationType.TAX_INVOICE_PDF_GENERATED);
        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(notification.getTemplateName()).isEqualTo("taxinvoice-pdf-generated");
        assertThat(notification.getDocumentNumber()).isEqualTo("TAXINV-2025-001");
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

    // ── DocumentIntakeStatUseCase: handleIntakeStat ────────────────────────────────────────

    @Test
    @DisplayName("handleIntakeStat should map event fields to DocumentIntakeStat and save")
    void handleIntakeStat_shouldMapAndSave() {
        DocumentReceivedTraceEvent event = new DocumentReceivedTraceEvent(
            UUID.randomUUID(),
            Instant.now(),
            "DOCUMENT_RECEIVED_TRACE",
            1,
            "doc-001",
            "corr-001",
            "document-intake-service",
            "RECEIVED",
            null,
            "doc-001",
            "TAX_INVOICE",
            "TIV-2024-001",
            "RECEIVED"
        );

        ArgumentCaptor<DocumentIntakeStat> captor = ArgumentCaptor.forClass(DocumentIntakeStat.class);
        when(documentIntakeStatRepository.save(any(DocumentIntakeStat.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        notificationService.handleIntakeStat(event);

        verify(documentIntakeStatRepository).save(captor.capture());
        DocumentIntakeStat saved = captor.getValue();
        assertThat(saved.getDocumentId()).isEqualTo("doc-001");
        assertThat(saved.getDocumentType()).isEqualTo("TAX_INVOICE");
        assertThat(saved.getDocumentNumber()).isEqualTo("TIV-2024-001");
        assertThat(saved.getStatus()).isEqualTo("RECEIVED");
        assertThat(saved.getSource()).isEqualTo("document-intake-service");
        assertThat(saved.getCorrelationId()).isEqualTo("corr-001");
        assertThat(saved.getOccurredAt()).isNotNull();
        assertThat(saved.getId()).isNotNull();
    }

}
