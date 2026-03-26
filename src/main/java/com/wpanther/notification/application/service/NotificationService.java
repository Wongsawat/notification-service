package com.wpanther.notification.application.service;

import com.wpanther.notification.application.usecase.DocumentReceivedEventUseCase;
import com.wpanther.notification.application.usecase.ProcessingEventUseCase;
import com.wpanther.notification.application.usecase.QueryNotificationUseCase;
import com.wpanther.notification.application.usecase.RetryNotificationUseCase;
import com.wpanther.notification.application.usecase.SagaEventUseCase;
import com.wpanther.notification.application.usecase.SendNotificationUseCase;
import com.wpanther.notification.domain.repository.NotificationRepository;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.application.port.in.event.DocumentReceivedCountingEvent;
import com.wpanther.notification.application.port.in.event.DocumentReceivedEvent;
import com.wpanther.notification.application.port.in.event.EbmsSentEvent;
import com.wpanther.notification.application.port.in.event.InvoiceProcessedEvent;
import com.wpanther.notification.application.port.in.event.PdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.PdfSignedEvent;
import com.wpanther.notification.application.port.in.event.TaxInvoiceProcessedEvent;
import com.wpanther.notification.application.port.in.event.XmlSignedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaCompletedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaFailedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaStartedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaStepCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing all six input port interfaces.
 *
 * <p>Thin orchestrator that delegates sending to {@link NotificationSendingService} and
 * dispatching to {@link NotificationDispatcherService}. Implements event handler use cases
 * (previously inline in Camel routes). Scheduled maintenance tasks are handled by
 * {@link com.wpanther.notification.infrastructure.adapter.in.scheduler.NotificationSchedulerAdapter}.</p>
 *
 * <p>Dependency chain (no cycles):
 * NotificationService → NotificationDispatcherService → NotificationSendingService</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService
        implements SendNotificationUseCase,
                   QueryNotificationUseCase,
                   RetryNotificationUseCase,
                   ProcessingEventUseCase,
                   DocumentReceivedEventUseCase,
                   SagaEventUseCase {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotificationRepository repository;
    private final NotificationSendingService sendingService;
    private final NotificationDispatcherService dispatcherService;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

    @Value("${app.notification.default-recipient:admin@example.com}")
    private String defaultRecipient;

    // ── SendNotificationUseCase ──────────────────────────────────────────────────────────

    @Override
    public Notification sendNotification(Notification notification) {
        return sendingService.sendNotification(notification);
    }

    @Override
    public Notification createAndSend(NotificationType type, NotificationChannel channel,
                                      String recipient, String templateName,
                                      Map<String, Object> templateVariables) {
        return sendingService.createAndSend(type, channel, recipient, templateName, templateVariables);
    }

    @Override
    public void dispatchPending(UUID id) {
        Notification notification = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Notification not found: " + id));

        if (notification.getStatus() != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                "Cannot dispatch non-PENDING notification (status=" + notification.getStatus() + ")");
        }

        dispatcherService.dispatchAsync(notification);
    }

    // ── QueryNotificationUseCase ─────────────────────────────────────────────────────────

    @Override
    public Optional<Notification> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Notification> findByInvoiceId(String invoiceId, int limit) {
        return repository.findByInvoiceId(invoiceId, limit);
    }

    @Override
    public List<Notification> findByStatus(NotificationStatus status, int limit) {
        return repository.findByStatus(status, limit);
    }

    @Override
    public Map<String, Long> getStatistics() {
        return sendingService.getStatistics();
    }

    // ── RetryNotificationUseCase ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void prepareAndDispatchRetry(UUID id) {
        Notification notification = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Notification not found: " + id));

        if (!notification.canRetry(maxRetries)) {
            throw new IllegalStateException("Cannot retry notification");
        }

        notification.prepareRetry();
        repository.save(notification);
        dispatcherService.dispatchAsync(notification);
    }

    // ── ProcessingEventUseCase ────────────────────────────────────────────────────────────

    @Override
    public void handleInvoiceProcessed(InvoiceProcessedEvent event) {
        log.info("Processing InvoiceProcessedEvent: invoiceId={}, invoiceNumber={}",
            event.getInvoiceId(), event.getInvoiceNumber());

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("invoiceId", event.getInvoiceId());
        templateVariables.put("invoiceNumber", event.getInvoiceNumber());
        templateVariables.put("totalAmount", String.format("%,.2f", event.getTotalAmount()));
        templateVariables.put("currency", event.getCurrency());
        templateVariables.put("processedAt", formatInstant(event.getOccurredAt()));

        Notification notification = Notification.createFromTemplate(
            NotificationType.INVOICE_PROCESSED,
            NotificationChannel.EMAIL,
            defaultRecipient,
            "invoice-processed",
            templateVariables
        );

        notification.setSubject("Invoice Processed: " + event.getInvoiceNumber());
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());

        dispatcherService.dispatchAsync(notification);
    }

    @Override
    public void handleTaxInvoiceProcessed(TaxInvoiceProcessedEvent event) {
        log.info("Processing TaxInvoiceProcessedEvent: invoiceId={}, invoiceNumber={}",
            event.getInvoiceId(), event.getInvoiceNumber());

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("invoiceId", event.getInvoiceId());
        templateVariables.put("invoiceNumber", event.getInvoiceNumber());
        templateVariables.put("totalAmount", String.format("%,.2f", event.getTotal()));
        templateVariables.put("currency", event.getCurrency());
        templateVariables.put("processedAt", formatInstant(event.getOccurredAt()));

        Notification notification = Notification.createFromTemplate(
            NotificationType.TAXINVOICE_PROCESSED,
            NotificationChannel.EMAIL,
            defaultRecipient,
            "taxinvoice-processed",
            templateVariables
        );

        notification.setSubject("Tax Invoice Processed: " + event.getInvoiceNumber());
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());

        dispatcherService.dispatchAsync(notification);
    }

    @Override
    public void handlePdfGenerated(PdfGeneratedEvent event) {
        log.info("Processing PdfGeneratedEvent: invoiceId={}, invoiceNumber={}",
            event.getInvoiceId(), event.getInvoiceNumber());

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("invoiceId", event.getInvoiceId());
        templateVariables.put("invoiceNumber", event.getInvoiceNumber());
        templateVariables.put("documentId", event.getDocumentId());
        templateVariables.put("documentUrl", event.getDocumentUrl());
        templateVariables.put("fileSize", formatFileSize(event.getFileSize()));
        templateVariables.put("generatedAt", formatInstant(event.getOccurredAt()));
        templateVariables.put("xmlEmbedded", event.isXmlEmbedded());
        templateVariables.put("digitallySigned", event.isDigitallySigned());

        Notification notification = Notification.createFromTemplate(
            NotificationType.PDF_GENERATED,
            NotificationChannel.EMAIL,
            defaultRecipient,
            "pdf-generated",
            templateVariables
        );

        notification.setSubject("PDF Invoice Ready: " + event.getInvoiceNumber());
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("documentUrl", event.getDocumentUrl());
        notification.addMetadata("documentId", event.getDocumentId());

        dispatcherService.dispatchAsync(notification);
    }

    @Override
    public void handlePdfSigned(PdfSignedEvent event) {
        log.info("Processing PdfSignedEvent: invoiceId={}, invoiceNumber={}, documentType={}",
            event.getInvoiceId(), event.getInvoiceNumber(), event.getDocumentType());

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("invoiceId", event.getInvoiceId());
        templateVariables.put("invoiceNumber", event.getInvoiceNumber());
        templateVariables.put("documentType", event.getDocumentType());
        templateVariables.put("signedDocumentId", event.getSignedDocumentId());
        templateVariables.put("signedPdfUrl", event.getSignedPdfUrl());
        templateVariables.put("signedPdfSize", formatFileSize(event.getSignedPdfSize()));
        templateVariables.put("transactionId", event.getTransactionId());
        templateVariables.put("signatureLevel", event.getSignatureLevel());
        templateVariables.put("signatureTimestamp", formatInstant(event.getSignatureTimestamp()));

        Notification notification = Notification.createFromTemplate(
            NotificationType.PDF_SIGNED,
            NotificationChannel.EMAIL,
            defaultRecipient,
            "pdf-signed",
            templateVariables
        );

        notification.setSubject("PDF Invoice Signed: " + event.getInvoiceNumber());
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("signedPdfUrl", event.getSignedPdfUrl());
        notification.addMetadata("signedDocumentId", event.getSignedDocumentId());
        notification.addMetadata("signatureLevel", event.getSignatureLevel());

        dispatcherService.dispatchAsync(notification);
    }

    @Override
    public void handleXmlSigned(XmlSignedEvent event) {
        log.info("Processing XmlSignedEvent: invoiceId={}, invoiceNumber={}, documentType={}",
            event.getInvoiceId(), event.getInvoiceNumber(), event.getDocumentType());

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("invoiceId", event.getInvoiceId());
        templateVariables.put("invoiceNumber", event.getInvoiceNumber());
        templateVariables.put("documentType", event.getDocumentType());
        templateVariables.put("signedAt", formatInstant(event.getOccurredAt()));

        Notification notification = Notification.createFromTemplate(
            NotificationType.XML_SIGNED,
            NotificationChannel.EMAIL,
            defaultRecipient,
            "xml-signed",
            templateVariables
        );

        notification.setSubject("XML Document Signed: " + event.getInvoiceNumber());
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("documentType", event.getDocumentType());

        dispatcherService.dispatchAsync(notification);
    }

    @Override
    public void handleEbmsSent(EbmsSentEvent event) {
        log.info("Processing EbmsSentEvent: documentId={}, documentType={}, ebmsMessageId={}",
            event.getDocumentId(), event.getDocumentType(), event.getEbmsMessageId());

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("documentId", event.getDocumentId());
        templateVariables.put("invoiceId", event.getInvoiceId() != null ? event.getInvoiceId() : "N/A");
        templateVariables.put("invoiceNumber", event.getInvoiceNumber() != null ? event.getInvoiceNumber() : "N/A");
        templateVariables.put("documentType", event.getDocumentType());
        templateVariables.put("ebmsMessageId", event.getEbmsMessageId());
        templateVariables.put("sentAt", formatInstant(event.getSentAt()));
        templateVariables.put("correlationId", event.getCorrelationId());

        String displayNumber = event.getInvoiceNumber() != null ? event.getInvoiceNumber() : event.getDocumentId();

        Notification notification = Notification.createFromTemplate(
            NotificationType.EBMS_SENT,
            NotificationChannel.EMAIL,
            defaultRecipient,
            "ebms-sent",
            templateVariables
        );

        notification.setSubject("Document Submitted to TRD: " + displayNumber);
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("ebmsMessageId", event.getEbmsMessageId());
        notification.addMetadata("documentType", event.getDocumentType());

        dispatcherService.dispatchAsync(notification);
    }

    // ── DocumentReceivedEventUseCase ─────────────────────────────────────────────────────

    @Override
    public void handleDocumentCounting(DocumentReceivedCountingEvent event) {
        log.info("Processing DocumentReceivedCountingEvent: documentId={}, correlationId={}",
            event.getDocumentId(), event.getCorrelationId());
        // Log only. Future: persist to database for total received count statistics.
    }

    @Override
    public void handleDocumentReceived(DocumentReceivedEvent event) {
        log.info("Processing DocumentReceivedEvent (statistics): documentId={}, documentType={}, correlationId={}",
            event.getDocumentId(), event.getDocumentType(), event.getCorrelationId());
        // Log only. Future: persist to database for type-specific statistics.
    }

    // ── SagaEventUseCase ─────────────────────────────────────────────────────────────────

    @Override
    public void handleSagaStarted(SagaStartedEvent event) {
        log.info("Saga started: sagaId={}, documentType={}, invoiceNumber={}",
            event.getSagaId(), event.getDocumentType(), event.getInvoiceNumber());
        // Log only — no notification created.
    }

    @Override
    public void handleSagaStepCompleted(SagaStepCompletedEvent event) {
        log.info("Saga step completed: sagaId={}, step={}, nextStep={}",
            event.getSagaId(), event.getCompletedStep(), event.getNextStep());
        // Log only — no notification created.
    }

    @Override
    public void handleSagaCompleted(SagaCompletedEvent event) {
        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("sagaId", event.getSagaId());
        templateVariables.put("documentId", event.getDocumentId());
        templateVariables.put("invoiceNumber", event.getInvoiceNumber() != null ? event.getInvoiceNumber() : "N/A");
        templateVariables.put("documentType", event.getDocumentType());
        templateVariables.put("stepsExecuted", event.getStepsExecuted());
        templateVariables.put("durationMs", event.getDurationMs());
        templateVariables.put("durationSec", String.format("%.2f", event.getDurationMs() / 1000.0));
        templateVariables.put("completedAt", formatInstant(event.getCompletedAt()));

        Notification notification = Notification.createFromTemplate(
            NotificationType.SAGA_COMPLETED,
            NotificationChannel.EMAIL,
            defaultRecipient,
            "saga-completed",
            templateVariables
        );

        notification.setSubject("Saga Completed: " +
            (event.getInvoiceNumber() != null ? event.getInvoiceNumber() : event.getDocumentId()));
        notification.setInvoiceId(event.getDocumentId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("sagaId", event.getSagaId());

        dispatcherService.dispatchAsync(notification);
    }

    @Override
    public void handleSagaFailed(SagaFailedEvent event) {
        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("sagaId", event.getSagaId());
        templateVariables.put("documentId", event.getDocumentId());
        templateVariables.put("invoiceNumber", event.getInvoiceNumber() != null ? event.getInvoiceNumber() : "N/A");
        templateVariables.put("documentType", event.getDocumentType());
        templateVariables.put("failedStep", event.getFailedStep());
        templateVariables.put("errorMessage", event.getErrorMessage());
        templateVariables.put("retryCount", event.getRetryCount());
        templateVariables.put("compensationInitiated",
            event.getCompensationInitiated() != null && event.getCompensationInitiated());
        templateVariables.put("failedAt", formatInstant(event.getFailedAt()));

        Notification notification = Notification.createFromTemplate(
            NotificationType.SAGA_FAILED,
            NotificationChannel.EMAIL,
            defaultRecipient,
            "saga-failed",
            templateVariables
        );

        notification.setSubject("URGENT: Saga Failed - " +
            (event.getInvoiceNumber() != null ? event.getInvoiceNumber() : event.getDocumentId()));
        notification.setInvoiceId(event.getDocumentId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("sagaId", event.getSagaId());
        notification.addMetadata("failedStep", event.getFailedStep());

        dispatcherService.dispatchAsync(notification);
    }

    // ── Private helpers ───────────────────────────────────────────────────────────────────

    private String formatInstant(Instant instant) {
        return instant != null
            ? DATE_FORMATTER.format(instant.atZone(ZoneId.systemDefault()))
            : "N/A";
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
