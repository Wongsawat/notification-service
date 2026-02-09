package com.wpanther.notification.integration;

import com.wpanther.notification.infrastructure.messaging.EbmsSentEvent;
import com.wpanther.notification.infrastructure.messaging.InvoiceProcessedEvent;
import com.wpanther.notification.infrastructure.messaging.PdfGeneratedEvent;
import com.wpanther.notification.infrastructure.messaging.PdfSignedEvent;
import com.wpanther.notification.infrastructure.messaging.TaxInvoiceProcessedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaCompletedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaFailedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kafka Consumer Integration Tests")
@Tag("integration")
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class KafkaConsumerIntegrationTest extends AbstractKafkaConsumerTest {

    @Test
    @DisplayName("Should consume InvoiceProcessedEvent and create notification")
    void shouldConsumeInvoiceProcessedEvent() {
        // Given
        String invoiceId = "INV-" + UUID.randomUUID();
        String invoiceNumber = "T0001-" + System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();

        InvoiceProcessedEvent event = new InvoiceProcessedEvent(
            invoiceId, invoiceNumber, new BigDecimal("15000.50"), "THB", correlationId
        );

        // When
        sendEvent("invoice.processed", invoiceId, event);

        // Then — await SENT status (full async flow completion)
        Map<String, Object> notification = awaitNotificationByInvoiceId(invoiceId);

        assertThat(notification.get("type")).isEqualTo("INVOICE_PROCESSED");
        assertThat(notification.get("channel")).isEqualTo("EMAIL");
        assertThat(notification.get("status")).isEqualTo("SENT");
        assertThat(notification.get("recipient")).isEqualTo("test-integration@example.com");
        assertThat(notification.get("template_name")).isEqualTo("invoice-processed");
        assertThat(notification.get("invoice_id")).isEqualTo(invoiceId);
        assertThat(notification.get("invoice_number")).isEqualTo(invoiceNumber);
        assertThat(notification.get("correlation_id")).isEqualTo(correlationId);
        assertThat((String) notification.get("subject")).contains(invoiceNumber);

        // template_variables stored as JSON TEXT — verify key values present
        String templateVars = (String) notification.get("template_variables");
        assertThat(templateVars).contains(invoiceId);
        assertThat(templateVars).contains(invoiceNumber);
        assertThat(templateVars).contains("THB");
    }

    @Test
    @DisplayName("Should consume TaxInvoiceProcessedEvent and create notification")
    void shouldConsumeTaxInvoiceProcessedEvent() {
        // Given
        String invoiceId = "TINV-" + UUID.randomUUID();
        String invoiceNumber = "TI0001-" + System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();

        TaxInvoiceProcessedEvent event = new TaxInvoiceProcessedEvent(
            invoiceId, invoiceNumber, new BigDecimal("25000.00"), "THB", correlationId
        );

        // When
        sendEvent("taxinvoice.processed", invoiceId, event);

        // Then
        Map<String, Object> notification = awaitNotificationByInvoiceId(invoiceId);

        assertThat(notification.get("type")).isEqualTo("TAXINVOICE_PROCESSED");
        assertThat(notification.get("channel")).isEqualTo("EMAIL");
        assertThat(notification.get("status")).isEqualTo("SENT");
        assertThat(notification.get("recipient")).isEqualTo("test-integration@example.com");
        assertThat(notification.get("template_name")).isEqualTo("taxinvoice-processed");
        assertThat(notification.get("invoice_id")).isEqualTo(invoiceId);
        assertThat(notification.get("invoice_number")).isEqualTo(invoiceNumber);
        assertThat(notification.get("correlation_id")).isEqualTo(correlationId);
        assertThat((String) notification.get("subject")).contains(invoiceNumber);

        String templateVars = (String) notification.get("template_variables");
        assertThat(templateVars).contains(invoiceId);
        assertThat(templateVars).contains(invoiceNumber);
        assertThat(templateVars).contains("THB");
    }

    @Test
    @DisplayName("Should consume PdfGeneratedEvent and create notification")
    void shouldConsumePdfGeneratedEvent() {
        // Given
        String invoiceId = "INV-" + UUID.randomUUID();
        String invoiceNumber = "T0001-" + System.currentTimeMillis();
        String documentId = "DOC-" + UUID.randomUUID();
        String documentUrl = "http://localhost:8084/api/v1/documents/" + documentId;
        String correlationId = UUID.randomUUID().toString();
        long fileSize = 125000; // 125 KB

        PdfGeneratedEvent event = new PdfGeneratedEvent(
            invoiceId, invoiceNumber, documentId, documentUrl, fileSize,
            true,  // xmlEmbedded
            false, // digitallySigned
            correlationId
        );

        // When
        sendEvent("pdf.generated", invoiceId, event);

        // Then
        Map<String, Object> notification = awaitNotificationByInvoiceId(invoiceId);

        assertThat(notification.get("type")).isEqualTo("PDF_GENERATED");
        assertThat(notification.get("channel")).isEqualTo("EMAIL");
        assertThat(notification.get("status")).isEqualTo("SENT");
        assertThat(notification.get("recipient")).isEqualTo("test-integration@example.com");
        assertThat(notification.get("template_name")).isEqualTo("pdf-generated");
        assertThat(notification.get("invoice_id")).isEqualTo(invoiceId);
        assertThat(notification.get("invoice_number")).isEqualTo(invoiceNumber);
        assertThat(notification.get("correlation_id")).isEqualTo(correlationId);
        assertThat((String) notification.get("subject")).contains(invoiceNumber);
        assertThat((String) notification.get("subject")).contains("PDF Invoice Ready");

        String templateVars = (String) notification.get("template_variables");
        assertThat(templateVars).contains(invoiceId);
        assertThat(templateVars).contains(invoiceNumber);
        assertThat(templateVars).contains(documentId);
        assertThat(templateVars).contains(documentUrl);
        // File size is formatted (e.g., "125 KB")
        assertThat(templateVars).contains("KB");
        // xmlEmbedded and digitallySigned are booleans
        assertThat(templateVars).contains("true");  // xmlEmbedded
        assertThat(templateVars).contains("false"); // digitallySigned
    }

    @Test
    @DisplayName("Should consume PdfSignedEvent and create notification")
    void shouldConsumePdfSignedEvent() {
        // Given
        String invoiceId = "INV-" + UUID.randomUUID();
        String invoiceNumber = "T0001-" + System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();
        String documentType = "INVOICE";
        String signedDocumentId = "SIGNED-DOC-" + UUID.randomUUID();
        String signedPdfUrl = "http://localhost:8084/api/v1/documents/" + signedDocumentId;
        long signedPdfSize = 130000; // 130 KB
        String transactionId = "TXN-" + UUID.randomUUID();
        String certificate = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA";
        String signatureLevel = "PAdES-BASELINE-T";
        Instant signatureTimestamp = Instant.now();

        PdfSignedEvent event = new PdfSignedEvent(
            correlationId, invoiceId, invoiceNumber, documentType,
            signedDocumentId, signedPdfUrl, signedPdfSize, transactionId,
            certificate, signatureLevel, signatureTimestamp
        );

        // When
        sendEvent("pdf.signed", invoiceId, event);

        // Then
        Map<String, Object> notification = awaitNotificationByInvoiceId(invoiceId);

        assertThat(notification.get("type")).isEqualTo("PDF_SIGNED");
        assertThat(notification.get("channel")).isEqualTo("EMAIL");
        assertThat(notification.get("status")).isEqualTo("SENT");
        assertThat(notification.get("recipient")).isEqualTo("test-integration@example.com");
        assertThat(notification.get("template_name")).isEqualTo("pdf-signed");
        assertThat(notification.get("invoice_id")).isEqualTo(invoiceId);
        assertThat(notification.get("invoice_number")).isEqualTo(invoiceNumber);
        assertThat(notification.get("correlation_id")).isEqualTo(correlationId);
        assertThat((String) notification.get("subject")).contains(invoiceNumber);
        assertThat((String) notification.get("subject")).contains("PDF Invoice Signed");

        String templateVars = (String) notification.get("template_variables");
        assertThat(templateVars).contains(invoiceId);
        assertThat(templateVars).contains(invoiceNumber);
        assertThat(templateVars).contains(documentType);
        assertThat(templateVars).contains(signedDocumentId);
        assertThat(templateVars).contains(signedPdfUrl);
        // File size is formatted (e.g., "130 KB")
        assertThat(templateVars).contains("KB");
        assertThat(templateVars).contains(transactionId);
        assertThat(templateVars).contains(signatureLevel);
        // Signature timestamp is formatted
        assertThat(templateVars).contains("signatureTimestamp");
    }

    @Test
    @DisplayName("Should consume EbmsSentEvent and create notification")
    void shouldConsumeEbmsSentEvent() {
        // Given
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceId = "INV-" + UUID.randomUUID();
        String invoiceNumber = "T0001-" + System.currentTimeMillis();
        String documentType = "INVOICE";
        String ebmsMessageId = "EBMS-" + UUID.randomUUID();
        Instant sentAt = Instant.now();
        String correlationId = UUID.randomUUID().toString();

        EbmsSentEvent event = new EbmsSentEvent(
            documentId, invoiceId, invoiceNumber, documentType,
            ebmsMessageId, sentAt, correlationId
        );

        // When
        sendEvent("ebms.sent", documentId, event);

        // Then - use correlationId for lookup since ebms events may not always have invoiceId
        Map<String, Object> notification = awaitNotificationByCorrelationId(correlationId);

        assertThat(notification.get("type")).isEqualTo("EBMS_SENT");
        assertThat(notification.get("channel")).isEqualTo("EMAIL");
        assertThat(notification.get("status")).isEqualTo("SENT");
        assertThat(notification.get("recipient")).isEqualTo("test-integration@example.com");
        assertThat(notification.get("template_name")).isEqualTo("ebms-sent");
        assertThat(notification.get("invoice_id")).isEqualTo(invoiceId);
        assertThat(notification.get("invoice_number")).isEqualTo(invoiceNumber);
        assertThat(notification.get("correlation_id")).isEqualTo(correlationId);
        assertThat((String) notification.get("subject")).contains("Document Submitted to TRD");
        assertThat((String) notification.get("subject")).contains(invoiceNumber);

        String templateVars = (String) notification.get("template_variables");
        assertThat(templateVars).contains(documentId);
        assertThat(templateVars).contains(invoiceId);
        assertThat(templateVars).contains(invoiceNumber);
        assertThat(templateVars).contains(documentType);
        assertThat(templateVars).contains(ebmsMessageId);
        assertThat(templateVars).contains(correlationId);
    }

    @Test
    @DisplayName("Should consume SagaCompletedEvent and create notification")
    void shouldConsumeSagaCompletedEvent() {
        // Given
        String sagaId = "SAGA-" + UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String documentType = "INVOICE";
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "T0001-" + System.currentTimeMillis();
        Integer stepsExecuted = 7;
        Instant startedAt = Instant.now().minusSeconds(120); // 2 minutes ago
        Instant completedAt = Instant.now();
        Long durationMs = 120000L; // 2 minutes

        SagaCompletedEvent event = new SagaCompletedEvent(
            sagaId, correlationId, documentType, documentId, invoiceNumber,
            stepsExecuted, startedAt, completedAt, durationMs
        );

        // When
        sendEvent("saga.lifecycle.completed", documentId, event);

        // Then - handler sets invoiceId to documentId, so use documentId for lookup
        Map<String, Object> notification = awaitNotificationByInvoiceId(documentId);

        assertThat(notification.get("type")).isEqualTo("SAGA_COMPLETED");
        assertThat(notification.get("channel")).isEqualTo("EMAIL");
        assertThat(notification.get("status")).isEqualTo("SENT");
        assertThat(notification.get("recipient")).isEqualTo("test-integration@example.com");
        assertThat(notification.get("template_name")).isEqualTo("saga-completed");
        assertThat(notification.get("invoice_id")).isEqualTo(documentId);
        assertThat(notification.get("invoice_number")).isEqualTo(invoiceNumber);
        assertThat(notification.get("correlation_id")).isEqualTo(correlationId);
        assertThat((String) notification.get("subject")).contains("Saga Completed");
        assertThat((String) notification.get("subject")).contains(invoiceNumber);

        String templateVars = (String) notification.get("template_variables");
        assertThat(templateVars).contains(sagaId);
        assertThat(templateVars).contains(documentId);
        assertThat(templateVars).contains(invoiceNumber);
        assertThat(templateVars).contains(documentType);
        assertThat(templateVars).contains(stepsExecuted.toString());
        assertThat(templateVars).contains(durationMs.toString());
        // Duration is formatted in seconds (e.g., "120.00")
        assertThat(templateVars).contains("durationSec");
    }

    @Test
    @DisplayName("Should consume SagaFailedEvent and create notification")
    void shouldConsumeSagaFailedEvent() {
        // Given
        String sagaId = "SAGA-" + UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String documentType = "INVOICE";
        String documentId = "DOC-" + UUID.randomUUID();
        String invoiceNumber = "T0001-" + System.currentTimeMillis();
        String failedStep = "xml-signing";
        String errorMessage = "Failed to sign XML document: Connection timeout";
        Integer retryCount = 2;
        Boolean compensationInitiated = true;
        Instant startedAt = Instant.now().minusSeconds(60); // 1 minute ago
        Instant failedAt = Instant.now();
        Long durationMs = 60000L; // 1 minute

        SagaFailedEvent event = new SagaFailedEvent(
            sagaId, correlationId, documentType, documentId, invoiceNumber,
            failedStep, errorMessage, retryCount, compensationInitiated,
            startedAt, failedAt, durationMs
        );

        // When
        sendEvent("saga.lifecycle.failed", documentId, event);

        // Then - handler sets invoiceId to documentId, so use documentId for lookup
        Map<String, Object> notification = awaitNotificationByInvoiceId(documentId);

        assertThat(notification.get("type")).isEqualTo("SAGA_FAILED");
        assertThat(notification.get("channel")).isEqualTo("EMAIL");
        assertThat(notification.get("status")).isEqualTo("SENT");
        assertThat(notification.get("recipient")).isEqualTo("test-integration@example.com");
        assertThat(notification.get("template_name")).isEqualTo("saga-failed");
        assertThat(notification.get("invoice_id")).isEqualTo(documentId);
        assertThat(notification.get("invoice_number")).isEqualTo(invoiceNumber);
        assertThat(notification.get("correlation_id")).isEqualTo(correlationId);
        assertThat((String) notification.get("subject")).contains("URGENT: Saga Failed");
        assertThat((String) notification.get("subject")).contains(invoiceNumber);

        String templateVars = (String) notification.get("template_variables");
        assertThat(templateVars).contains(sagaId);
        assertThat(templateVars).contains(documentId);
        assertThat(templateVars).contains(invoiceNumber);
        assertThat(templateVars).contains(documentType);
        assertThat(templateVars).contains(failedStep);
        assertThat(templateVars).contains(errorMessage);
        assertThat(templateVars).contains(retryCount.toString());
        // compensationInitiated is a boolean
        assertThat(templateVars).contains("true");  // compensationInitiated
        assertThat(templateVars).contains("failedAt"); // formatted timestamp
    }
}
