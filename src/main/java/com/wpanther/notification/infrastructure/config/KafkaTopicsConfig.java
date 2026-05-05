package com.wpanther.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kafka topic names bound from {@code kafka.topics.*} configuration.
 * Replaces 18 individual {@code @Value} topic parameters in NotificationEventRoutes.
 */
@ConfigurationProperties(prefix = "kafka.topics")
public record KafkaTopicsConfig(
    String invoiceProcessed,
    String taxinvoiceProcessed,
    String pdfGenerated,
    String pdfGeneratedTaxInvoice,
    String pdfSigned,
    String xmlSigned,
    String ebmsSent,
    String notificationDlq,
    String sagaLifecycleStarted,
    String sagaLifecycleStepCompleted,
    String sagaLifecycleCompleted,
    String sagaLifecycleFailed,
    String traceDocumentReceived,
    // --- 9 new fields below ---
    String receiptProcessed,
    String cancellationNoteProcessed,
    String debitCreditNoteProcessed,
    String abbreviatedTaxInvoiceProcessed,
    String pdfGeneratedReceipt,
    String pdfGeneratedCancellationNote,
    String pdfGeneratedDebitCreditNote,
    String pdfGeneratedAbbreviatedTaxInvoice,
    String documentArchived
) {}
