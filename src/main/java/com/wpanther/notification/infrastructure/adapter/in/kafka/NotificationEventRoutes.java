package com.wpanther.notification.infrastructure.adapter.in.kafka;

import com.wpanther.notification.application.port.in.event.DocumentReceivedTraceEvent;
import com.wpanther.notification.application.port.in.event.EbmsSentEvent;
import com.wpanther.notification.application.port.in.event.InvoiceProcessedEvent;
import com.wpanther.notification.application.port.in.event.InvoicePdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.PdfSignedEvent;
import com.wpanther.notification.application.port.in.event.TaxInvoicePdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.TaxInvoiceProcessedEvent;
import com.wpanther.notification.application.port.in.event.XmlSignedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaCompletedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaFailedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaStartedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaStepCompletedEvent;
import com.wpanther.notification.application.usecase.DocumentIntakeStatUseCase;
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
    private final SagaEventUseCase sagaEventUseCase;
    private final DocumentIntakeStatUseCase documentIntakeStatUseCase;
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
            SagaEventUseCase sagaEventUseCase,
            DocumentIntakeStatUseCase documentIntakeStatUseCase,
            @Value("${app.notification.enabled:true}") boolean notificationEnabled,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroup,
            @Value("${spring.kafka.bootstrap-servers}") String kafkaBrokers,
            KafkaTopicsConfig topics,
            @Value("${app.notification.dlq-max-redeliveries:3}") int dlqMaxRedeliveries,
            @Value("${app.notification.dlq-redelivery-delay-ms:1000}") long dlqRedeliveryDelayMs,
            @Value("${app.notification.dlq-back-off-multiplier:2}") double dlqBackOffMultiplier,
            @Value("${app.notification.dlq-max-redelivery-delay-ms:30000}") long dlqMaxRedeliveryDelayMs) {
        this.processingEventUseCase = processingEventUseCase;
        this.sagaEventUseCase = sagaEventUseCase;
        this.documentIntakeStatUseCase = documentIntakeStatUseCase;
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
         * All 18 routes share this single global error handler.
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
            .log("Created notification for invoice processed: ${header.documentNumber}");

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
            .log("Created notification for tax invoice processed: ${header.documentNumber}");

        // Route 3: PDF Generated Events
        from("kafka:" + topics.pdfGenerated() + kafkaOptions)
            .routeId("notification-pdf-generated")
            .log("Received InvoicePdfGeneratedEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping message")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, InvoicePdfGeneratedEvent.class)
            .process(this::handleInvoicePdfGenerated)
            .log("Created notification for PDF generated: ${header.documentNumber}");

        // Route 4: Tax Invoice PDF Generated Events
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
            .log("Created notification for tax invoice PDF generated: ${header.documentNumber}");

        // Route 5: PDF Signed Events
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
            .log("Created notification for PDF signed: ${header.documentNumber}");

        // Route 6: XML Signed Events
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
            .log("Created notification for XML signed: ${header.documentNumber}");

        // Route 8: ebMS Sent Events
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
            .log("Created notification for ebMS sent: ${header.documentNumber}");

        // Route 15: Saga Started Events (logging only)
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

        // Route 16: Saga Step Completed Events (logging only - no notifications)
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

        // Route 17: Saga Completed Events (creates email notification)
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

        // Route 18: Saga Failed Events (creates urgent email notification)
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

        // Route 19: Document Intake Trace Events (all statuses: RECEIVED, VALIDATED, FORWARDED, INVALID)
        from("kafka:" + topics.traceDocumentReceived() + kafkaOptions)
            .routeId("notification-trace-document-received")
            .log("Received DocumentReceivedTraceEvent from Kafka")
            .choice()
                .when(exchange -> !notificationEnabled)
                    .log("Notifications disabled, skipping trace event")
                    .stop()
            .end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedTraceEvent.class)
            .process(this::handleIntakeStat)
            .log("Persisted intake stat: documentId=${header.documentId}, status=${header.status}");
    }

    private void handleInvoiceProcessed(Exchange exchange) {
        InvoiceProcessedEvent event = exchange.getIn().getBody(InvoiceProcessedEvent.class);
        processingEventUseCase.handleInvoiceProcessed(event);
        exchange.getIn().setHeader("documentNumber", event.getDocumentNumber());
    }

    private void handleTaxInvoiceProcessed(Exchange exchange) {
        TaxInvoiceProcessedEvent event = exchange.getIn().getBody(TaxInvoiceProcessedEvent.class);
        processingEventUseCase.handleTaxInvoiceProcessed(event);
        exchange.getIn().setHeader("documentNumber", event.getDocumentNumber());
    }

    private void handleInvoicePdfGenerated(Exchange exchange) {
        InvoicePdfGeneratedEvent event = exchange.getIn().getBody(InvoicePdfGeneratedEvent.class);
        processingEventUseCase.handleInvoicePdfGenerated(event);
        exchange.getIn().setHeader("documentNumber", event.getDocumentNumber());
    }

    private void handleTaxInvoicePdfGenerated(Exchange exchange) {
        TaxInvoicePdfGeneratedEvent event = exchange.getIn().getBody(TaxInvoicePdfGeneratedEvent.class);
        processingEventUseCase.handleTaxInvoicePdfGenerated(event);
        exchange.getIn().setHeader("documentNumber", event.getDocumentNumber());
    }

    private void handlePdfSigned(Exchange exchange) {
        PdfSignedEvent event = exchange.getIn().getBody(PdfSignedEvent.class);
        processingEventUseCase.handlePdfSigned(event);
        exchange.getIn().setHeader("documentNumber", event.getDocumentNumber());
    }

    private void handleXmlSigned(Exchange exchange) {
        XmlSignedEvent event = exchange.getIn().getBody(XmlSignedEvent.class);
        processingEventUseCase.handleXmlSigned(event);
        exchange.getIn().setHeader("documentNumber", event.getDocumentNumber());
    }

    private void handleEbmsSent(Exchange exchange) {
        EbmsSentEvent event = exchange.getIn().getBody(EbmsSentEvent.class);
        processingEventUseCase.handleEbmsSent(event);
        exchange.getIn().setHeader("documentNumber",
            event.getDocumentNumber() != null ? event.getDocumentNumber() : event.getDocumentId());
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

    private void handleIntakeStat(Exchange exchange) {
        DocumentReceivedTraceEvent event = exchange.getIn().getBody(DocumentReceivedTraceEvent.class);
        documentIntakeStatUseCase.handleIntakeStat(event);
        exchange.getIn().setHeader("documentId", event.getDocumentId());
        exchange.getIn().setHeader("status", event.getStatus());
    }
}
