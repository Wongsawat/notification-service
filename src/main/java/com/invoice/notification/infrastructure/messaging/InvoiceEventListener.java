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
     * Handle invoice received event
     */
    @KafkaListener(
        topics = "${kafka.topics.invoice-received}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "invoiceReceivedKafkaListenerContainerFactory"
    )
    public void handleInvoiceReceived(InvoiceReceivedEvent event) {
        if (!notificationEnabled) {
            log.debug("Notifications disabled, skipping event: {}", event.getEventId());
            return;
        }

        try {
            log.info("Received InvoiceReceivedEvent: invoiceId={}, invoiceNumber={}",
                event.getInvoiceId(), event.getInvoiceNumber());

            Map<String, Object> templateVariables = new HashMap<>();
            templateVariables.put("invoiceId", event.getInvoiceId());
            templateVariables.put("invoiceNumber", event.getInvoiceNumber());
            templateVariables.put("receivedAt", event.getReceivedAt().format(DATE_FORMATTER));
            templateVariables.put("source", event.getSource());

            Notification notification = Notification.createFromTemplate(
                NotificationType.INVOICE_RECEIVED,
                NotificationChannel.EMAIL,
                defaultRecipient,
                "invoice-received",
                templateVariables
            );

            notification.setSubject("Invoice Received: " + event.getInvoiceNumber());
            notification.setInvoiceId(event.getInvoiceId());
            notification.setInvoiceNumber(event.getInvoiceNumber());
            notification.setCorrelationId(event.getCorrelationId());

            notificationService.sendNotificationAsync(notification);

            log.info("Created notification for invoice received: invoiceNumber={}",
                event.getInvoiceNumber());

        } catch (Exception e) {
            log.error("Failed to handle InvoiceReceivedEvent: invoiceId={}",
                event.getInvoiceId(), e);
            // Don't rethrow - we don't want to block event processing
        }
    }

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
