package com.invoice.notification.infrastructure.messaging;

import com.invoice.notification.application.service.NotificationService;
import com.invoice.notification.domain.model.Notification;
import com.invoice.notification.domain.model.NotificationChannel;
import com.invoice.notification.domain.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka listener for invoice-related events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceEventListener {

    private final NotificationService notificationService;

    @Value("${app.notification.default-recipient:admin@example.com}")
    private String defaultRecipient;

    @Value("${app.notification.enabled:true}")
    private boolean notificationEnabled;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Handle invoice processed event
     */
    @KafkaListener(
        topics = "${kafka.topics.invoice-processed}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "invoiceProcessedKafkaListenerContainerFactory"
    )
    public void handleInvoiceProcessed(InvoiceProcessedEvent event) {
        if (!notificationEnabled) {
            log.debug("Notifications disabled, skipping event: {}", event.getEventId());
            return;
        }

        try {
            log.info("Received InvoiceProcessedEvent: invoiceId={}, invoiceNumber={}",
                event.getInvoiceId(), event.getInvoiceNumber());

            Map<String, Object> templateVariables = new HashMap<>();
            templateVariables.put("invoiceId", event.getInvoiceId());
            templateVariables.put("invoiceNumber", event.getInvoiceNumber());
            templateVariables.put("totalAmount", String.format("%,.2f", event.getTotalAmount()));
            templateVariables.put("currency", event.getCurrency());
            templateVariables.put("processedAt", event.getProcessedAt().format(DATE_FORMATTER));

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

            notificationService.sendNotificationAsync(notification);

            log.info("Created notification for invoice processed: invoiceNumber={}",
                event.getInvoiceNumber());

        } catch (Exception e) {
            log.error("Failed to handle InvoiceProcessedEvent: invoiceId={}",
                event.getInvoiceId(), e);
        }
    }

    /**
     * Handle tax invoice processed event
     */
    @KafkaListener(
        topics = "${kafka.topics.taxinvoice-processed}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "taxInvoiceProcessedKafkaListenerContainerFactory"
    )
    public void handleTaxInvoiceProcessed(TaxInvoiceProcessedEvent event) {
        if (!notificationEnabled) {
            log.debug("Notifications disabled, skipping event: {}", event.getEventId());
            return;
        }

        try {
            log.info("Received TaxInvoiceProcessedEvent: invoiceId={}, invoiceNumber={}",
                event.getInvoiceId(), event.getInvoiceNumber());

            Map<String, Object> templateVariables = new HashMap<>();
            templateVariables.put("invoiceId", event.getInvoiceId());
            templateVariables.put("invoiceNumber", event.getInvoiceNumber());
            templateVariables.put("totalAmount", String.format("%,.2f", event.getTotal()));
            templateVariables.put("currency", event.getCurrency());
            templateVariables.put("processedAt", event.getOccurredAt() != null
                ? DATE_FORMATTER.format(event.getOccurredAt().atZone(java.time.ZoneId.systemDefault()))
                : "N/A");

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

            notificationService.sendNotificationAsync(notification);

            log.info("Created notification for tax invoice processed: invoiceNumber={}",
                event.getInvoiceNumber());

        } catch (Exception e) {
            log.error("Failed to handle TaxInvoiceProcessedEvent: invoiceId={}",
                event.getInvoiceId(), e);
        }
    }

    /**
     * Handle PDF generated event
     */
    @KafkaListener(
        topics = "${kafka.topics.pdf-generated}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "pdfGeneratedKafkaListenerContainerFactory"
    )
    public void handlePdfGenerated(PdfGeneratedEvent event) {
        if (!notificationEnabled) {
            log.debug("Notifications disabled, skipping event: {}", event.getEventId());
            return;
        }

        try {
            log.info("Received PdfGeneratedEvent: invoiceId={}, invoiceNumber={}",
                event.getInvoiceId(), event.getInvoiceNumber());

            Map<String, Object> templateVariables = new HashMap<>();
            templateVariables.put("invoiceId", event.getInvoiceId());
            templateVariables.put("invoiceNumber", event.getInvoiceNumber());
            templateVariables.put("documentId", event.getDocumentId());
            templateVariables.put("documentUrl", event.getDocumentUrl());
            templateVariables.put("fileSize", formatFileSize(event.getFileSize()));
            templateVariables.put("generatedAt", event.getGeneratedAt().format(DATE_FORMATTER));
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

            notificationService.sendNotificationAsync(notification);

            log.info("Created notification for PDF generated: invoiceNumber={}",
                event.getInvoiceNumber());

        } catch (Exception e) {
            log.error("Failed to handle PdfGeneratedEvent: invoiceId={}",
                event.getInvoiceId(), e);
        }
    }

    /**
     * Handle PDF signed event
     */
    @KafkaListener(
        topics = "${kafka.topics.pdf-signed}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "pdfSignedKafkaListenerContainerFactory"
    )
    public void handlePdfSigned(PdfSignedEvent event) {
        if (!notificationEnabled) {
            log.debug("Notifications disabled, skipping event: {}", event.getEventId());
            return;
        }

        try {
            log.info("Received PdfSignedEvent: invoiceId={}, invoiceNumber={}, documentType={}",
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
            templateVariables.put("signatureTimestamp", event.getSignatureTimestamp().format(DATE_FORMATTER));

            // Map documentType to appropriate NotificationType
            NotificationType notificationType = mapDocumentTypeToNotificationType(event.getDocumentType());

            Notification notification = Notification.createFromTemplate(
                notificationType,
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

            notificationService.sendNotificationAsync(notification);

            log.info("Created notification for PDF signed: invoiceNumber={}, documentType={}",
                event.getInvoiceNumber(), event.getDocumentType());

        } catch (Exception e) {
            log.error("Failed to handle PdfSignedEvent: invoiceId={}",
                event.getInvoiceId(), e);
        }
    }

    /**
     * Map document type to appropriate notification type
     */
    private NotificationType mapDocumentTypeToNotificationType(String documentType) {
        if (documentType == null) {
            return NotificationType.PDF_SIGNED;
        }

        // For now, all signed PDFs use PDF_SIGNED notification type
        // In the future, we might want different notification types for different document types
        return NotificationType.PDF_SIGNED;
    }

    /**
     * Format file size to human-readable string
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
