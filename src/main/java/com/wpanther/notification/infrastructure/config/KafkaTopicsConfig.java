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
    String traceDocumentReceived
) {}
