package com.wpanther.notification.infrastructure.messaging;

import com.wpanther.notification.application.port.in.DocumentReceivedEventUseCase;
import com.wpanther.notification.application.port.in.ProcessingEventUseCase;
import com.wpanther.notification.application.port.in.SagaEventUseCase;
import com.wpanther.notification.infrastructure.config.KafkaTopicsConfig;
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

    public NotificationEventRoutes(
            ProcessingEventUseCase processingEventUseCase,
            DocumentReceivedEventUseCase documentReceivedEventUseCase,
            SagaEventUseCase sagaEventUseCase,
            @Value("${app.notification.enabled:true}") boolean notificationEnabled,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroup,
            @Value("${spring.kafka.bootstrap-servers}") String kafkaBrokers,
            KafkaTopicsConfig topics) {
        this.processingEventUseCase = processingEventUseCase;
        this.documentReceivedEventUseCase = documentReceivedEventUseCase;
        this.sagaEventUseCase = sagaEventUseCase;
        this.notificationEnabled = notificationEnabled;
        this.consumerGroup = consumerGroup;
        this.kafkaBrokers = kafkaBrokers;
        this.topics = topics;
    }

    @Override
    public void configure() throws Exception {
        // Global error handler - Dead Letter Channel with exponential backoff
        errorHandler(deadLetterChannel("kafka:" + topics.notificationDlq() + "?brokers=" + kafkaBrokers)
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
