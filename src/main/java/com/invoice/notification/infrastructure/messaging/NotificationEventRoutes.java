package com.invoice.notification.infrastructure.messaging;

import com.invoice.notification.application.service.NotificationService;
import com.invoice.notification.domain.model.Notification;
import com.invoice.notification.domain.model.NotificationChannel;
import com.invoice.notification.domain.model.NotificationType;
import com.invoice.notification.infrastructure.messaging.InvoiceProcessedEvent;
import com.invoice.notification.infrastructure.messaging.PdfGeneratedEvent;
import com.invoice.notification.infrastructure.messaging.PdfSignedEvent;
import com.invoice.notification.infrastructure.messaging.TaxInvoiceProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Apache Camel routes for consuming notification events from Kafka.
 *
 * Replaces the Spring Kafka @KafkaListener implementation with Camel routes.
 * Each route consumes from a specific topic and creates appropriate notifications.
 */
@Component
@Slf4j
public class NotificationEventRoutes extends RouteBuilder {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotificationService notificationService;
    private final String defaultRecipient;
    private final boolean notificationEnabled;
    private final String consumerGroup;
    private final String kafkaBrokers;

    // Topic names
    private final String invoiceProcessedTopic;
    private final String taxInvoiceProcessedTopic;
    private final String pdfGeneratedTopic;
    private final String pdfSignedTopic;
    private final String dlqTopic;

    public NotificationEventRoutes(
            NotificationService notificationService,
            @Value("${app.notification.default-recipient:admin@example.com}") String defaultRecipient,
            @Value("${app.notification.enabled:true}") boolean notificationEnabled,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroup,
            @Value("${spring.kafka.bootstrap-servers}") String kafkaBrokers,
            @Value("${kafka.topics.invoice-processed}") String invoiceProcessedTopic,
            @Value("${kafka.topics.taxinvoice-processed}") String taxInvoiceProcessedTopic,
            @Value("${kafka.topics.pdf-generated}") String pdfGeneratedTopic,
            @Value("${kafka.topics.pdf-signed}") String pdfSignedTopic,
            @Value("${kafka.topics.notification-dlq:notification.dlq}") String dlqTopic) {
        this.notificationService = notificationService;
        this.defaultRecipient = defaultRecipient;
        this.notificationEnabled = notificationEnabled;
        this.consumerGroup = consumerGroup;
        this.kafkaBrokers = kafkaBrokers;
        this.invoiceProcessedTopic = invoiceProcessedTopic;
        this.taxInvoiceProcessedTopic = taxInvoiceProcessedTopic;
        this.pdfGeneratedTopic = pdfGeneratedTopic;
        this.pdfSignedTopic = pdfSignedTopic;
        this.dlqTopic = dlqTopic;
    }

    @Override
    public void configure() throws Exception {
        // Global error handler - Dead Letter Channel with exponential backoff
        errorHandler(deadLetterChannel("kafka:" + dlqTopic + "?brokers=" + kafkaBrokers)
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(30000)
            .logExhausted(true)
            .logRetryAttempted(true)
            .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));

        // Common Kafka consumer options
        String kafkaOptions = "?brokers=" + kafkaBrokers
            + "&groupId=" + consumerGroup
            + "&autoOffsetReset=earliest"
            + "&autoCommitEnable=false"
            + "&breakOnFirstError=true";

        // Route 1: Invoice Processed Events
        from("kafka:" + invoiceProcessedTopic + kafkaOptions)
            .routeId("notification-invoice-processed")
            .log("Received InvoiceProcessedEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping message")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, InvoiceProcessedEvent.class)
            .process(this::handleInvoiceProcessed)
            .log("Created notification for invoice processed: ${header.invoiceNumber}");

        // Route 2: Tax Invoice Processed Events
        from("kafka:" + taxInvoiceProcessedTopic + kafkaOptions)
            .routeId("notification-taxinvoice-processed")
            .log("Received TaxInvoiceProcessedEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping message")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, TaxInvoiceProcessedEvent.class)
            .process(this::handleTaxInvoiceProcessed)
            .log("Created notification for tax invoice processed: ${header.invoiceNumber}");

        // Route 3: PDF Generated Events
        from("kafka:" + pdfGeneratedTopic + kafkaOptions)
            .routeId("notification-pdf-generated")
            .log("Received PdfGeneratedEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping message")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, PdfGeneratedEvent.class)
            .process(this::handlePdfGenerated)
            .log("Created notification for PDF generated: ${header.invoiceNumber}");

        // Route 4: PDF Signed Events
        from("kafka:" + pdfSignedTopic + kafkaOptions)
            .routeId("notification-pdf-signed")
            .log("Received PdfSignedEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping message")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, PdfSignedEvent.class)
            .process(this::handlePdfSigned)
            .log("Created notification for PDF signed: ${header.invoiceNumber}");
    }

    /**
     * Process InvoiceProcessedEvent and create notification
     */
    private void handleInvoiceProcessed(Exchange exchange) {
        InvoiceProcessedEvent event = exchange.getIn().getBody(InvoiceProcessedEvent.class);

        log.info("Processing InvoiceProcessedEvent: invoiceId={}, invoiceNumber={}",
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

        // Set header for logging
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    /**
     * Process TaxInvoiceProcessedEvent and create notification
     */
    private void handleTaxInvoiceProcessed(Exchange exchange) {
        TaxInvoiceProcessedEvent event = exchange.getIn().getBody(TaxInvoiceProcessedEvent.class);

        log.info("Processing TaxInvoiceProcessedEvent: invoiceId={}, invoiceNumber={}",
            event.getInvoiceId(), event.getInvoiceNumber());

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("invoiceId", event.getInvoiceId());
        templateVariables.put("invoiceNumber", event.getInvoiceNumber());
        templateVariables.put("totalAmount", String.format("%,.2f", event.getTotal()));
        templateVariables.put("currency", event.getCurrency());
        templateVariables.put("processedAt", event.getOccurredAt() != null
            ? DATE_FORMATTER.format(event.getOccurredAt().atZone(ZoneId.systemDefault()))
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

        // Set header for logging
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    /**
     * Process PdfGeneratedEvent and create notification
     */
    private void handlePdfGenerated(Exchange exchange) {
        PdfGeneratedEvent event = exchange.getIn().getBody(PdfGeneratedEvent.class);

        log.info("Processing PdfGeneratedEvent: invoiceId={}, invoiceNumber={}",
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

        // Set header for logging
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    /**
     * Process PdfSignedEvent and create notification
     */
    private void handlePdfSigned(Exchange exchange) {
        PdfSignedEvent event = exchange.getIn().getBody(PdfSignedEvent.class);

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
        templateVariables.put("signatureTimestamp", event.getSignatureTimestamp().format(DATE_FORMATTER));

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

        notificationService.sendNotificationAsync(notification);

        // Set header for logging
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    /**
     * Format file size to human-readable string
     */
    private String formatFileSize(Long bytes) {
        if (bytes == null) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
