package com.wpanther.notification.infrastructure.messaging;

import com.wpanther.notification.application.service.NotificationService;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.infrastructure.messaging.EbmsSentEvent;
import com.wpanther.notification.infrastructure.messaging.InvoiceProcessedEvent;
import com.wpanther.notification.infrastructure.messaging.PdfGeneratedEvent;
import com.wpanther.notification.infrastructure.messaging.PdfSignedEvent;
import com.wpanther.notification.infrastructure.messaging.TaxInvoiceProcessedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaCompletedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaFailedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaStartedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaStepCompletedEvent;
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
    private final String ebmsSentTopic;
    private final String dlqTopic;

    // Document-type-specific topics for statistics
    private final String documentReceivedCountingTopic; // For counting (all documents before validation)
    private final String taxInvoiceReceivedTopic;
    private final String invoiceReceivedTopic;
    private final String receiptReceivedTopic;
    private final String debitCreditNoteReceivedTopic;
    private final String cancellationReceivedTopic;
    private final String abbreviatedReceivedTopic;

    // Saga lifecycle topics
    private final String sagaLifecycleStartedTopic;
    private final String sagaLifecycleStepCompletedTopic;
    private final String sagaLifecycleCompletedTopic;
    private final String sagaLifecycleFailedTopic;

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
            @Value("${kafka.topics.ebms-sent:ebms.sent}") String ebmsSentTopic,
            @Value("${kafka.topics.notification-dlq:notification.dlq}") String dlqTopic,
            @Value("${kafka.topics.document-received:document.received}") String documentReceivedCountingTopic,
            @Value("${kafka.topics.tax-invoice-received:document.received.tax-invoice}") String taxInvoiceReceivedTopic,
            @Value("${kafka.topics.invoice-received:document.received.invoice}") String invoiceReceivedTopic,
            @Value("${kafka.topics.receipt-received:document.received.receipt}") String receiptReceivedTopic,
            @Value("${kafka.topics.debit-credit-note-received:document.received.debit-credit-note}") String debitCreditNoteReceivedTopic,
            @Value("${kafka.topics.cancellation-received:document.received.cancellation}") String cancellationReceivedTopic,
            @Value("${kafka.topics.abbreviated-received:document.received.abbreviated}") String abbreviatedReceivedTopic,
            @Value("${kafka.topics.saga-lifecycle-started}") String sagaLifecycleStartedTopic,
            @Value("${kafka.topics.saga-lifecycle-step-completed}") String sagaLifecycleStepCompletedTopic,
            @Value("${kafka.topics.saga-lifecycle-completed}") String sagaLifecycleCompletedTopic,
            @Value("${kafka.topics.saga-lifecycle-failed}") String sagaLifecycleFailedTopic) {
        this.notificationService = notificationService;
        this.defaultRecipient = defaultRecipient;
        this.notificationEnabled = notificationEnabled;
        this.consumerGroup = consumerGroup;
        this.kafkaBrokers = kafkaBrokers;
        this.invoiceProcessedTopic = invoiceProcessedTopic;
        this.taxInvoiceProcessedTopic = taxInvoiceProcessedTopic;
        this.pdfGeneratedTopic = pdfGeneratedTopic;
        this.pdfSignedTopic = pdfSignedTopic;
        this.ebmsSentTopic = ebmsSentTopic;
        this.dlqTopic = dlqTopic;
        this.documentReceivedCountingTopic = documentReceivedCountingTopic;
        this.taxInvoiceReceivedTopic = taxInvoiceReceivedTopic;
        this.invoiceReceivedTopic = invoiceReceivedTopic;
        this.receiptReceivedTopic = receiptReceivedTopic;
        this.debitCreditNoteReceivedTopic = debitCreditNoteReceivedTopic;
        this.cancellationReceivedTopic = cancellationReceivedTopic;
        this.abbreviatedReceivedTopic = abbreviatedReceivedTopic;
        this.sagaLifecycleStartedTopic = sagaLifecycleStartedTopic;
        this.sagaLifecycleStepCompletedTopic = sagaLifecycleStepCompletedTopic;
        this.sagaLifecycleCompletedTopic = sagaLifecycleCompletedTopic;
        this.sagaLifecycleFailedTopic = sagaLifecycleFailedTopic;
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

        // Route 5: Document Received Counting Events (before validation - all documents)
        // This lightweight event tracks ALL received documents regardless of validation outcome
        from("kafka:" + documentReceivedCountingTopic + kafkaOptions)
            .routeId("notification-document-counting")
            .log("Received DocumentReceivedCountingEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping counting event")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedCountingEvent.class)
            .process(this::handleDocumentReceivedCounting)
            .log("Processed document counting event: documentId=${header.documentId}");

        // Route 6: Tax Invoice Document Received Events (after validation)
        // Statistics event for validated tax invoice documents
        from("kafka:" + taxInvoiceReceivedTopic + kafkaOptions)
            .routeId("notification-tax-invoice-received")
            .log("Received DocumentReceivedEvent (TAX_INVOICE) from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping statistics event")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedEvent.class)
            .process(this::handleDocumentReceivedStats)
            .log("Processed tax invoice statistics event");

        // Route 7: Invoice Document Received Events (after validation)
        // Statistics event for validated invoice documents
        from("kafka:" + invoiceReceivedTopic + kafkaOptions)
            .routeId("notification-invoice-received")
            .log("Received DocumentReceivedEvent (INVOICE) from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping statistics event")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedEvent.class)
            .process(this::handleDocumentReceivedStats)
            .log("Processed invoice statistics event");

        // Additional routes for other document types follow the same pattern
        // Route 8: Receipt Document Received Events (after validation)
        from("kafka:" + receiptReceivedTopic + kafkaOptions)
            .routeId("notification-receipt-received")
            .log("Received DocumentReceivedEvent (RECEIPT) from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping statistics event")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedEvent.class)
            .process(this::handleDocumentReceivedStats)
            .log("Processed receipt statistics event");

        // Route 9: Debit/Credit Note Document Received Events (after validation)
        from("kafka:" + debitCreditNoteReceivedTopic + kafkaOptions)
            .routeId("notification-debit-credit-note-received")
            .log("Received DocumentReceivedEvent (DEBIT_CREDIT_NOTE) from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping statistics event")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedEvent.class)
            .process(this::handleDocumentReceivedStats)
            .log("Processed debit/credit note statistics event");

        // Route 10: Cancellation Note Document Received Events (after validation)
        from("kafka:" + cancellationReceivedTopic + kafkaOptions)
            .routeId("notification-cancellation-received")
            .log("Received DocumentReceivedEvent (CANCELLATION) from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping statistics event")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedEvent.class)
            .process(this::handleDocumentReceivedStats)
            .log("Processed cancellation note statistics event");

        // Route 11: Abbreviated Tax Invoice Document Received Events (after validation)
        from("kafka:" + abbreviatedReceivedTopic + kafkaOptions)
            .routeId("notification-abbreviated-received")
            .log("Received DocumentReceivedEvent (ABBREVIATED) from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping statistics event")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedEvent.class)
            .process(this::handleDocumentReceivedStats)
            .log("Processed abbreviated tax invoice statistics event");

        // Route 12: ebMS Sent Events
        from("kafka:" + ebmsSentTopic + kafkaOptions)
            .routeId("notification-ebms-sent")
            .log("Received EbmsSentEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping message")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, EbmsSentEvent.class)
            .process(this::handleEbmsSent)
            .log("Created notification for ebMS sent: ${header.invoiceNumber}");

        // Route 13: Saga Started Events (logging only)
        from("kafka:" + sagaLifecycleStartedTopic + kafkaOptions)
            .routeId("notification-saga-started")
            .log("Received SagaStartedEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping saga event")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, SagaStartedEvent.class)
            .process(this::handleSagaStarted)
            .log("Processed saga started: sagaId=${header.sagaId}");

        // Route 14: Saga Step Completed Events (logging only - no notifications)
        from("kafka:" + sagaLifecycleStepCompletedTopic + kafkaOptions)
            .routeId("notification-saga-step-completed")
            .log("Received SagaStepCompletedEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping saga event")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, SagaStepCompletedEvent.class)
            .process(this::handleSagaStepCompleted)
            .log("Processed saga step completed: step=${header.completedStep}");

        // Route 15: Saga Completed Events (creates email notification)
        from("kafka:" + sagaLifecycleCompletedTopic + kafkaOptions)
            .routeId("notification-saga-completed")
            .log("Received SagaCompletedEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping saga event")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, SagaCompletedEvent.class)
            .process(this::handleSagaCompleted)
            .log("Created notification for saga completed: sagaId=${header.sagaId}");

        // Route 16: Saga Failed Events (creates urgent email notification)
        from("kafka:" + sagaLifecycleFailedTopic + kafkaOptions)
            .routeId("notification-saga-failed")
            .log("Received SagaFailedEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping saga event")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, SagaFailedEvent.class)
            .process(this::handleSagaFailed)
            .log("Created notification for saga failed: sagaId=${header.sagaId}");
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

    /**
     * Process DocumentReceivedCountingEvent and track total received count.
     * This lightweight event is published BEFORE validation, so ALL documents are counted.
     *
     * Future enhancement: Persist to database for accurate total received count tracking.
     */
    private void handleDocumentReceivedCounting(Exchange exchange) {
        DocumentReceivedCountingEvent event = exchange.getIn().getBody(DocumentReceivedCountingEvent.class);

        log.info("Processing DocumentReceivedCountingEvent: documentId={}, correlationId={}",
            event.getDocumentId(), event.getCorrelationId());

        // For now, just log the event. Future: persist to database for statistics
        // This counts ALL received documents regardless of validation outcome

        // Set headers for logging
        exchange.getIn().setHeader("documentId", event.getDocumentId());
        exchange.getIn().setHeader("correlationId", event.getCorrelationId());
    }

    /**
     * Process DocumentReceivedEvent and track type-specific statistics.
     * This event is published AFTER validation, so only VALIDATED documents are tracked.
     *
     * Future enhancement: Persist to database for type-specific statistics.
     */
    private void handleDocumentReceivedStats(Exchange exchange) {
        DocumentReceivedEvent event = exchange.getIn().getBody(DocumentReceivedEvent.class);

        log.info("Processing DocumentReceivedEvent (statistics): documentId={}, documentType={}, correlationId={}",
            event.getDocumentId(), event.getDocumentType(), event.getCorrelationId());

        // For now, just log the event. Future: persist to database for statistics
        // This tracks validated documents by type

        // Set headers for logging
        exchange.getIn().setHeader("documentId", event.getDocumentId());
        exchange.getIn().setHeader("documentType", event.getDocumentType());
        exchange.getIn().setHeader("correlationId", event.getCorrelationId());
    }

    /**
     * Process EbmsSentEvent and create notification
     */
    private void handleEbmsSent(Exchange exchange) {
        EbmsSentEvent event = exchange.getIn().getBody(EbmsSentEvent.class);

        log.info("Processing EbmsSentEvent: documentId={}, documentType={}, ebmsMessageId={}",
            event.getDocumentId(), event.getDocumentType(), event.getEbmsMessageId());

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("documentId", event.getDocumentId());
        templateVariables.put("invoiceId", event.getInvoiceId() != null ? event.getInvoiceId() : "N/A");
        templateVariables.put("invoiceNumber", event.getInvoiceNumber() != null ? event.getInvoiceNumber() : "N/A");
        templateVariables.put("documentType", event.getDocumentType());
        templateVariables.put("ebmsMessageId", event.getEbmsMessageId());
        templateVariables.put("sentAt", event.getSentAt().format(DATE_FORMATTER));
        templateVariables.put("correlationId", event.getCorrelationId());

        String displayNumber = event.getInvoiceNumber() != null ? event.getInvoiceNumber() : event.getDocumentId();
        String subject = "Document Submitted to TRD: " + displayNumber;

        Notification notification = Notification.createFromTemplate(
            NotificationType.EBMS_SENT,
            NotificationChannel.EMAIL,
            defaultRecipient,
            "ebms-sent",
            templateVariables
        );

        notification.setSubject(subject);
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("ebmsMessageId", event.getEbmsMessageId());
        notification.addMetadata("documentType", event.getDocumentType());

        notificationService.sendNotificationAsync(notification);

        // Set header for logging
        exchange.getIn().setHeader("invoiceNumber", displayNumber);
    }

    /**
     * Process SagaStartedEvent (logging only, no notification)
     */
    private void handleSagaStarted(Exchange exchange) {
        SagaStartedEvent event = exchange.getIn().getBody(SagaStartedEvent.class);
        log.info("Saga started: sagaId={}, documentType={}, invoiceNumber={}",
            event.getSagaId(), event.getDocumentType(), event.getInvoiceNumber());
        // Just log - no notification created
        exchange.getIn().setHeader("sagaId", event.getSagaId());
    }

    /**
     * Process SagaStepCompletedEvent (logging only, no notification)
     */
    private void handleSagaStepCompleted(Exchange exchange) {
        SagaStepCompletedEvent event = exchange.getIn().getBody(SagaStepCompletedEvent.class);
        log.info("Saga step completed: sagaId={}, step={}, nextStep={}",
            event.getSagaId(), event.getCompletedStep(), event.getNextStep());
        // Just log - no notification created (per user requirement)
        exchange.getIn().setHeader("completedStep", event.getCompletedStep());
    }

    /**
     * Process SagaCompletedEvent and create success notification
     */
    private void handleSagaCompleted(Exchange exchange) {
        SagaCompletedEvent event = exchange.getIn().getBody(SagaCompletedEvent.class);

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("sagaId", event.getSagaId());
        templateVariables.put("documentId", event.getDocumentId());
        templateVariables.put("invoiceNumber", event.getInvoiceNumber() != null ? event.getInvoiceNumber() : "N/A");
        templateVariables.put("documentType", event.getDocumentType());
        templateVariables.put("stepsExecuted", event.getStepsExecuted());
        templateVariables.put("durationMs", event.getDurationMs());
        templateVariables.put("durationSec", String.format("%.2f", event.getDurationMs() / 1000.0));
        templateVariables.put("completedAt", event.getCompletedAt() != null
            ? DATE_FORMATTER.format(event.getCompletedAt().atZone(ZoneId.systemDefault()))
            : "N/A");

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

        notificationService.sendNotificationAsync(notification);
        exchange.getIn().setHeader("sagaId", event.getSagaId());
    }

    /**
     * Process SagaFailedEvent and create urgent notification
     */
    private void handleSagaFailed(Exchange exchange) {
        SagaFailedEvent event = exchange.getIn().getBody(SagaFailedEvent.class);

        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("sagaId", event.getSagaId());
        templateVariables.put("documentId", event.getDocumentId());
        templateVariables.put("invoiceNumber", event.getInvoiceNumber() != null ? event.getInvoiceNumber() : "N/A");
        templateVariables.put("documentType", event.getDocumentType());
        templateVariables.put("failedStep", event.getFailedStep());
        templateVariables.put("errorMessage", event.getErrorMessage());
        templateVariables.put("retryCount", event.getRetryCount());
        templateVariables.put("compensationInitiated", event.getCompensationInitiated() != null && event.getCompensationInitiated());
        templateVariables.put("failedAt", event.getFailedAt() != null
            ? DATE_FORMATTER.format(event.getFailedAt().atZone(ZoneId.systemDefault()))
            : "N/A");

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

        notificationService.sendNotificationAsync(notification);
        exchange.getIn().setHeader("sagaId", event.getSagaId());
    }
}
