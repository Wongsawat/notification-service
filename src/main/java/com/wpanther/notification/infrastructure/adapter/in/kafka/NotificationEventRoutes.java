package com.wpanther.notification.infrastructure.adapter.in.kafka;

import com.wpanther.notification.application.port.in.event.DocumentReceivedCountingEvent;
import com.wpanther.notification.application.port.in.event.DocumentReceivedEvent;
import com.wpanther.notification.application.port.in.event.EbmsSentEvent;
import com.wpanther.notification.application.port.in.event.InvoiceProcessedEvent;
import com.wpanther.notification.application.port.in.event.PdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.PdfSignedEvent;
import com.wpanther.notification.application.port.in.event.TaxInvoicePdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.TaxInvoiceProcessedEvent;
import com.wpanther.notification.application.port.in.event.XmlSignedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaCompletedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaFailedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaStartedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaStepCompletedEvent;
import com.wpanther.notification.application.usecase.DocumentReceivedEventUseCase;
import com.wpanther.notification.application.usecase.ProcessingEventUseCase;
import com.wpanther.notification.application.usecase.SagaEventUseCase;
import com.wpanther.notification.infrastructure.config.KafkaTopicsConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for consuming notification events from Kafka.
 *
 * Replaces the Spring Kafka @KafkaListener implementation with Camel routes.
 * Each route consumes from a specific topic and creates appropriate notifications.
 */
@Component
@Slf4j
public class NotificationEventRoutes extends RouteBuilder {

    private final ProcessingEventUseCase processingEventUseCase;
    private final DocumentReceivedEventUseCase documentReceivedEventUseCase;
    private final SagaEventUseCase sagaEventUseCase;
    private final boolean notificationEnabled;
    private final String consumerGroup;
    private final String kafkaBrokers;
    private final KafkaTopicsConfig topics;
    private final int dlqMaxRedeliveries;
    private final long dlqRedeliveryDelayMs;
    private final double dlqBackOffMultiplier;
    private final long dlqMaxRedeliveryDelayMs;

    public NotificationEventRoutes(
            ProcessingEventUseCase processingEventUseCase,
            DocumentReceivedEventUseCase documentReceivedEventUseCase,
            SagaEventUseCase sagaEventUseCase,
            @Value("${app.notification.enabled:true}") boolean notificationEnabled,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroup,
            @Value("${spring.kafka.bootstrap-servers}") String kafkaBrokers,
            KafkaTopicsConfig topics,
            @Value("${app.notification.dlq-max-redeliveries:3}") int dlqMaxRedeliveries,
            @Value("${app.notification.dlq-redelivery-delay-ms:1000}") long dlqRedeliveryDelayMs,
            @Value("${app.notification.dlq-back-off-multiplier:2}") double dlqBackOffMultiplier,
            @Value("${app.notification.dlq-max-redelivery-delay-ms:30000}") long dlqMaxRedeliveryDelayMs) {
        this.processingEventUseCase = processingEventUseCase;
        this.documentReceivedEventUseCase = documentReceivedEventUseCase;
        this.sagaEventUseCase = sagaEventUseCase;
        this.notificationEnabled = notificationEnabled;
        this.consumerGroup = consumerGroup;
        this.kafkaBrokers = kafkaBrokers;
        this.topics = topics;
        this.dlqMaxRedeliveries = dlqMaxRedeliveries;
        this.dlqRedeliveryDelayMs = dlqRedeliveryDelayMs;
        this.dlqBackOffMultiplier = dlqBackOffMultiplier;
        this.dlqMaxRedeliveryDelayMs = dlqMaxRedeliveryDelayMs;
    }

    @Override
    public void configure() throws Exception {
        /*
         * Error handling strategy: Dead Letter Channel + breakOnFirstError
         * ----------------------------------------------------------------
         * All 16 routes share this single global error handler.
         *
         * breakOnFirstError=true (set on each Kafka consumer URI below) tells
         * the Camel Kafka component to stop polling new messages from a partition
         * as soon as one message fails. The same failed message is retried in-place
         * (by the Dead Letter Channel) before the partition resumes.
         *
         * Worst-case partition stall per poison pill:
         *   delay_total = redeliveryDelay * (backOffMultiplier^0 + ... + backOffMultiplier^(n-1))
         *   With defaults: 1000ms + 2000ms + 4000ms = 7000ms of sleep between attempts,
         *   plus per-attempt processing time (typically < 1s for this service).
         *
         * After all redelivery attempts are exhausted the message is forwarded to the
         * Dead Letter Queue topic below and the partition resumes normally.
         *
         * Operational requirements:
         *   - Monitor topic '${topics.notificationDlq()}' for messages — presence means
         *     a poison pill survived all retries. Alerts should fire on non-zero consumer lag.
         *   - Tune DLQ_MAX_REDELIVERIES / DLQ_REDELIVERY_DELAY_MS env vars to adjust
         *     the stall window vs. retry aggressiveness trade-off.
         */
        log.info("Configuring Dead Letter Channel: dlq={}, maxRedeliveries={}, "
                + "redeliveryDelayMs={}, backOffMultiplier={}, maxRedeliveryDelayMs={}",
                topics.notificationDlq(), dlqMaxRedeliveries, dlqRedeliveryDelayMs,
                dlqBackOffMultiplier, dlqMaxRedeliveryDelayMs);

        errorHandler(deadLetterChannel("kafka:" + topics.notificationDlq() + "?brokers=" + kafkaBrokers)
            .maximumRedeliveries(dlqMaxRedeliveries)
            .redeliveryDelay(dlqRedeliveryDelayMs)
            .useExponentialBackOff()
            .backOffMultiplier(dlqBackOffMultiplier)
            .maximumRedeliveryDelay(dlqMaxRedeliveryDelayMs)
            .logExhausted(true)
            .logRetryAttempted(true)
            .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));

        // Common Kafka consumer options.
        // breakOnFirstError=true: halt partition polling on failure so the Dead Letter
        // Channel can retry the same message in-place before advancing the offset.
        String kafkaOptions = "?brokers=" + kafkaBrokers
            + "&groupId=" + consumerGroup
            + "&autoOffsetReset=earliest"
            + "&autoCommitEnable=false"
            + "&breakOnFirstError=true";

        // Route 1: Invoice Processed Events
        from("kafka:" + topics.invoiceProcessed() + kafkaOptions)
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
        from("kafka:" + topics.taxinvoiceProcessed() + kafkaOptions)
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
        from("kafka:" + topics.pdfGenerated() + kafkaOptions)
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

        // Route 17: Tax Invoice PDF Generated Events
        from("kafka:" + topics.pdfGeneratedTaxInvoice() + kafkaOptions)
            .routeId("notification-taxinvoice-pdf-generated")
            .log("Received TaxInvoicePdfGeneratedEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping message")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, TaxInvoicePdfGeneratedEvent.class)
            .process(this::handleTaxInvoicePdfGenerated)
            .log("Created notification for tax invoice PDF generated: ${header.taxInvoiceNumber}");

        // Route 4: PDF Signed Events
        from("kafka:" + topics.pdfSigned() + kafkaOptions)
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

        // Route 5: XML Signed Events
        from("kafka:" + topics.xmlSigned() + kafkaOptions)
            .routeId("notification-xml-signed")
            .log("Received XmlSignedEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping message")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, XmlSignedEvent.class)
            .process(this::handleXmlSigned)
            .log("Created notification for XML signed: ${header.invoiceNumber}");

        // Route 6: Document Received Counting Events (before validation - all documents)
        // This lightweight event tracks ALL received documents regardless of validation outcome
        from("kafka:" + topics.documentReceived() + kafkaOptions)
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
        from("kafka:" + topics.taxInvoiceReceived() + kafkaOptions)
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
        from("kafka:" + topics.invoiceReceived() + kafkaOptions)
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
        from("kafka:" + topics.receiptReceived() + kafkaOptions)
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
        from("kafka:" + topics.debitCreditNoteReceived() + kafkaOptions)
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
        from("kafka:" + topics.cancellationReceived() + kafkaOptions)
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
        from("kafka:" + topics.abbreviatedReceived() + kafkaOptions)
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
        from("kafka:" + topics.ebmsSent() + kafkaOptions)
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
        from("kafka:" + topics.sagaLifecycleStarted() + kafkaOptions)
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
        from("kafka:" + topics.sagaLifecycleStepCompleted() + kafkaOptions)
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
        from("kafka:" + topics.sagaLifecycleCompleted() + kafkaOptions)
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
        from("kafka:" + topics.sagaLifecycleFailed() + kafkaOptions)
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

    private void handleInvoiceProcessed(Exchange exchange) {
        InvoiceProcessedEvent event = exchange.getIn().getBody(InvoiceProcessedEvent.class);
        processingEventUseCase.handleInvoiceProcessed(event);
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    private void handleTaxInvoiceProcessed(Exchange exchange) {
        TaxInvoiceProcessedEvent event = exchange.getIn().getBody(TaxInvoiceProcessedEvent.class);
        processingEventUseCase.handleTaxInvoiceProcessed(event);
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    private void handlePdfGenerated(Exchange exchange) {
        PdfGeneratedEvent event = exchange.getIn().getBody(PdfGeneratedEvent.class);
        processingEventUseCase.handlePdfGenerated(event);
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    private void handleTaxInvoicePdfGenerated(Exchange exchange) {
        TaxInvoicePdfGeneratedEvent event = exchange.getIn().getBody(TaxInvoicePdfGeneratedEvent.class);
        processingEventUseCase.handleTaxInvoicePdfGenerated(event);
        exchange.getIn().setHeader("taxInvoiceNumber", event.getTaxInvoiceNumber());
    }

    private void handlePdfSigned(Exchange exchange) {
        PdfSignedEvent event = exchange.getIn().getBody(PdfSignedEvent.class);
        processingEventUseCase.handlePdfSigned(event);
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    private void handleXmlSigned(Exchange exchange) {
        XmlSignedEvent event = exchange.getIn().getBody(XmlSignedEvent.class);
        processingEventUseCase.handleXmlSigned(event);
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    private void handleEbmsSent(Exchange exchange) {
        EbmsSentEvent event = exchange.getIn().getBody(EbmsSentEvent.class);
        processingEventUseCase.handleEbmsSent(event);
        exchange.getIn().setHeader("invoiceNumber",
            event.getInvoiceNumber() != null ? event.getInvoiceNumber() : event.getDocumentId());
    }

    private void handleDocumentReceivedCounting(Exchange exchange) {
        DocumentReceivedCountingEvent event = exchange.getIn().getBody(DocumentReceivedCountingEvent.class);
        documentReceivedEventUseCase.handleDocumentCounting(event);
        exchange.getIn().setHeader("documentId", event.getDocumentId());
    }

    private void handleDocumentReceivedStats(Exchange exchange) {
        DocumentReceivedEvent event = exchange.getIn().getBody(DocumentReceivedEvent.class);
        documentReceivedEventUseCase.handleDocumentReceived(event);
        exchange.getIn().setHeader("documentId", event.getDocumentId());
    }

    private void handleSagaStarted(Exchange exchange) {
        SagaStartedEvent event = exchange.getIn().getBody(SagaStartedEvent.class);
        sagaEventUseCase.handleSagaStarted(event);
        exchange.getIn().setHeader("sagaId", event.getSagaId());
    }

    private void handleSagaStepCompleted(Exchange exchange) {
        SagaStepCompletedEvent event = exchange.getIn().getBody(SagaStepCompletedEvent.class);
        sagaEventUseCase.handleSagaStepCompleted(event);
        exchange.getIn().setHeader("completedStep", event.getCompletedStep());
    }

    private void handleSagaCompleted(Exchange exchange) {
        SagaCompletedEvent event = exchange.getIn().getBody(SagaCompletedEvent.class);
        sagaEventUseCase.handleSagaCompleted(event);
        exchange.getIn().setHeader("sagaId", event.getSagaId());
    }

    private void handleSagaFailed(Exchange exchange) {
        SagaFailedEvent event = exchange.getIn().getBody(SagaFailedEvent.class);
        sagaEventUseCase.handleSagaFailed(event);
        exchange.getIn().setHeader("sagaId", event.getSagaId());
    }
}
