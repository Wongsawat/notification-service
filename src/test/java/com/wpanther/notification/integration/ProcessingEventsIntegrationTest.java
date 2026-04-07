package com.wpanther.notification.integration;

import com.wpanther.notification.application.port.in.event.InvoiceProcessedEvent;
import com.wpanther.notification.application.port.in.event.TaxInvoiceProcessedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for invoice.processed and taxinvoice.processed Kafka topic consumption.
 *
 * <p>These tests require external containers started via:
 * <pre>
 *   ./scripts/test-containers-start.sh --with-debezium
 * </pre>
 * PostgreSQL: localhost:5433  |  Kafka: localhost:9093  |  Debezium: localhost:8083
 *
 * <p>Run with:
 * <pre>
 *   mvn verify -Pintegration -Dtest=ProcessingEventsIntegrationTest
 * </pre>
 */
@DisplayName("Processing Events Integration Tests")
@Tag("integration")
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class ProcessingEventsIntegrationTest extends AbstractKafkaConsumerTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: find notification by document_number column
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> getNotificationByDocumentNumber(String documentNumber) {
        List<Map<String, Object>> results = testJdbcTemplate.queryForList(
            "SELECT * FROM notifications WHERE document_number = ?", documentNumber);
        return results.isEmpty() ? null : results.get(0);
    }

    private Map<String, Object> awaitNotificationByDocumentNumber(String documentNumber) {
        await().atMost(2, TimeUnit.MINUTES)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> {
                   Map<String, Object> n = getNotificationByDocumentNumber(documentNumber);
                   return n != null && "SENT".equals(n.get("status"));
               });
        return getNotificationByDocumentNumber(documentNumber);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // invoice.processed
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("invoice.processed topic")
    class InvoiceProcessed {

        @Test
        @DisplayName("Should create SENT notification with exact subject prefix 'Invoice Processed: '")
        void shouldCreateNotificationWithExactSubjectPrefix() {
            // Given
            String documentId     = "INV-" + UUID.randomUUID();
            String documentNumber = "INV-PREFIX-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            InvoiceProcessedEvent event = new InvoiceProcessedEvent(
                documentId, documentNumber, new BigDecimal("5000.00"), "THB", correlationId
            );

            // When
            sendEvent("invoice.processed", documentId, event);

            // Then
            Map<String, Object> notification = awaitNotificationByDocumentId(documentId);

            assertThat(notification.get("subject"))
                .isEqualTo("Invoice Processed: " + documentNumber);
        }

        @Test
        @DisplayName("Should format totalAmount with thousands separator (e.g., 15,000.50)")
        void shouldFormatTotalAmountWithThousandsSeparator() {
            // Given
            String documentId     = "INV-" + UUID.randomUUID();
            String documentNumber = "INV-FMT-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            InvoiceProcessedEvent event = new InvoiceProcessedEvent(
                documentId, documentNumber, new BigDecimal("15000.50"), "THB", correlationId
            );

            // When
            sendEvent("invoice.processed", documentId, event);

            // Then
            Map<String, Object> notification = awaitNotificationByDocumentId(documentId);

            String templateVars = (String) notification.get("template_variables");
            assertThat(templateVars).contains("15,000.50");
        }

        @Test
        @DisplayName("Should include processedAt key in template variables")
        void shouldIncludeProcessedAtInTemplateVariables() {
            // Given
            String documentId     = "INV-" + UUID.randomUUID();
            String documentNumber = "INV-TS-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            InvoiceProcessedEvent event = new InvoiceProcessedEvent(
                documentId, documentNumber, new BigDecimal("3000.00"), "THB", correlationId
            );

            // When
            sendEvent("invoice.processed", documentId, event);

            // Then
            Map<String, Object> notification = awaitNotificationByDocumentId(documentId);

            String templateVars = (String) notification.get("template_variables");
            assertThat(templateVars).contains("processedAt");
        }

        @Test
        @DisplayName("Should include currency in template variables")
        void shouldIncludeCurrencyInTemplateVariables() {
            // Given
            String documentId     = "INV-" + UUID.randomUUID();
            String documentNumber = "INV-CUR-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            InvoiceProcessedEvent event = new InvoiceProcessedEvent(
                documentId, documentNumber, new BigDecimal("9999.99"), "USD", correlationId
            );

            // When
            sendEvent("invoice.processed", documentId, event);

            // Then
            Map<String, Object> notification = awaitNotificationByDocumentId(documentId);

            String templateVars = (String) notification.get("template_variables");
            assertThat(templateVars).contains("USD");
        }

        @Test
        @DisplayName("Should store document_number column matching event documentNumber")
        void shouldStoreDocumentNumberColumn() {
            // Given
            String documentId     = "INV-" + UUID.randomUUID();
            String documentNumber = "INV-COL-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            InvoiceProcessedEvent event = new InvoiceProcessedEvent(
                documentId, documentNumber, new BigDecimal("1000.00"), "THB", correlationId
            );

            // When
            sendEvent("invoice.processed", documentId, event);

            // Then — can locate notification by document_number column
            Map<String, Object> notification = awaitNotificationByDocumentNumber(documentNumber);

            assertThat(notification.get("type")).isEqualTo("INVOICE_PROCESSED");
            assertThat(notification.get("status")).isEqualTo("SENT");
            assertThat(notification.get("document_id")).isEqualTo(documentId);
            assertThat(notification.get("correlation_id")).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("Should process two sequential InvoiceProcessedEvents and create two distinct SENT notifications")
        void shouldProcessTwoSequentialEventsAndCreateDistinctNotifications() {
            // Given
            String documentId1     = "INV-SEQ1-" + UUID.randomUUID();
            String documentNumber1 = "INV-SEQ1-" + System.currentTimeMillis();
            String documentId2     = "INV-SEQ2-" + UUID.randomUUID();
            String documentNumber2 = "INV-SEQ2-" + (System.currentTimeMillis() + 1);
            String correlationId1  = UUID.randomUUID().toString();
            String correlationId2  = UUID.randomUUID().toString();

            InvoiceProcessedEvent event1 = new InvoiceProcessedEvent(
                documentId1, documentNumber1, new BigDecimal("10000.00"), "THB", correlationId1
            );
            InvoiceProcessedEvent event2 = new InvoiceProcessedEvent(
                documentId2, documentNumber2, new BigDecimal("20000.00"), "THB", correlationId2
            );

            // When
            sendEvent("invoice.processed", documentId1, event1);
            sendEvent("invoice.processed", documentId2, event2);

            // Then — both notifications reach SENT status independently
            Map<String, Object> notification1 = awaitNotificationByDocumentId(documentId1);
            Map<String, Object> notification2 = awaitNotificationByDocumentId(documentId2);

            assertThat(notification1.get("status")).isEqualTo("SENT");
            assertThat(notification1.get("document_number")).isEqualTo(documentNumber1);
            assertThat(notification1.get("correlation_id")).isEqualTo(correlationId1);

            assertThat(notification2.get("status")).isEqualTo("SENT");
            assertThat(notification2.get("document_number")).isEqualTo(documentNumber2);
            assertThat(notification2.get("correlation_id")).isEqualTo(correlationId2);

            // Notifications are for different documents
            assertThat(notification1.get("id")).isNotEqualTo(notification2.get("id"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // taxinvoice.processed
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("taxinvoice.processed topic")
    class TaxInvoiceProcessed {

        @Test
        @DisplayName("Should create SENT notification with exact subject prefix 'Tax Invoice Processed: '")
        void shouldCreateNotificationWithExactSubjectPrefix() {
            // Given
            String documentId     = "TINV-" + UUID.randomUUID();
            String documentNumber = "TINV-PREFIX-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            TaxInvoiceProcessedEvent event = new TaxInvoiceProcessedEvent(
                documentId, documentNumber, new BigDecimal("25000.00"), "THB", correlationId
            );

            // When
            sendEvent("taxinvoice.processed", documentId, event);

            // Then
            Map<String, Object> notification = awaitNotificationByDocumentId(documentId);

            assertThat(notification.get("subject"))
                .isEqualTo("Tax Invoice Processed: " + documentNumber);
        }

        @Test
        @DisplayName("Should format total with thousands separator and map it to 'totalAmount' template key")
        void shouldFormatTotalAndMapToTotalAmountKey() {
            // Given
            String documentId     = "TINV-" + UUID.randomUUID();
            String documentNumber = "TINV-FMT-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            TaxInvoiceProcessedEvent event = new TaxInvoiceProcessedEvent(
                documentId, documentNumber, new BigDecimal("1234567.89"), "THB", correlationId
            );

            // When
            sendEvent("taxinvoice.processed", documentId, event);

            // Then — service maps TaxInvoiceProcessedEvent.total → templateVars["totalAmount"]
            Map<String, Object> notification = awaitNotificationByDocumentId(documentId);

            String templateVars = (String) notification.get("template_variables");
            assertThat(templateVars)
                .as("template_variables should contain 'totalAmount' key")
                .contains("totalAmount");
            assertThat(templateVars)
                .as("totalAmount should be formatted with thousands separator")
                .contains("1,234,567.89");
        }

        @Test
        @DisplayName("Should include processedAt key in template variables")
        void shouldIncludeProcessedAtInTemplateVariables() {
            // Given
            String documentId     = "TINV-" + UUID.randomUUID();
            String documentNumber = "TINV-TS-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            TaxInvoiceProcessedEvent event = new TaxInvoiceProcessedEvent(
                documentId, documentNumber, new BigDecimal("8000.00"), "THB", correlationId
            );

            // When
            sendEvent("taxinvoice.processed", documentId, event);

            // Then
            Map<String, Object> notification = awaitNotificationByDocumentId(documentId);

            String templateVars = (String) notification.get("template_variables");
            assertThat(templateVars).contains("processedAt");
        }

        @Test
        @DisplayName("Should store document_number column matching event documentNumber")
        void shouldStoreDocumentNumberColumn() {
            // Given
            String documentId     = "TINV-" + UUID.randomUUID();
            String documentNumber = "TINV-COL-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            TaxInvoiceProcessedEvent event = new TaxInvoiceProcessedEvent(
                documentId, documentNumber, new BigDecimal("4500.00"), "THB", correlationId
            );

            // When
            sendEvent("taxinvoice.processed", documentId, event);

            // Then — can locate notification by document_number column
            Map<String, Object> notification = awaitNotificationByDocumentNumber(documentNumber);

            assertThat(notification.get("type")).isEqualTo("TAXINVOICE_PROCESSED");
            assertThat(notification.get("status")).isEqualTo("SENT");
            assertThat(notification.get("document_id")).isEqualTo(documentId);
            assertThat(notification.get("correlation_id")).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("Should use 'taxinvoice-processed' email template")
        void shouldUseTaxInvoiceProcessedTemplate() {
            // Given
            String documentId     = "TINV-" + UUID.randomUUID();
            String documentNumber = "TINV-TPL-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            TaxInvoiceProcessedEvent event = new TaxInvoiceProcessedEvent(
                documentId, documentNumber, new BigDecimal("7500.00"), "THB", correlationId
            );

            // When
            sendEvent("taxinvoice.processed", documentId, event);

            // Then — tax invoice uses its own dedicated template (not 'invoice-processed')
            Map<String, Object> notification = awaitNotificationByDocumentId(documentId);

            assertThat(notification.get("template_name")).isEqualTo("taxinvoice-processed");
            assertThat(notification.get("type")).isEqualTo("TAXINVOICE_PROCESSED");
        }

        @Test
        @DisplayName("Should process two sequential TaxInvoiceProcessedEvents and create two distinct SENT notifications")
        void shouldProcessTwoSequentialEventsAndCreateDistinctNotifications() {
            // Given
            String documentId1     = "TINV-SEQ1-" + UUID.randomUUID();
            String documentNumber1 = "TINV-SEQ1-" + System.currentTimeMillis();
            String documentId2     = "TINV-SEQ2-" + UUID.randomUUID();
            String documentNumber2 = "TINV-SEQ2-" + (System.currentTimeMillis() + 1);
            String correlationId1  = UUID.randomUUID().toString();
            String correlationId2  = UUID.randomUUID().toString();

            TaxInvoiceProcessedEvent event1 = new TaxInvoiceProcessedEvent(
                documentId1, documentNumber1, new BigDecimal("30000.00"), "THB", correlationId1
            );
            TaxInvoiceProcessedEvent event2 = new TaxInvoiceProcessedEvent(
                documentId2, documentNumber2, new BigDecimal("45000.00"), "THB", correlationId2
            );

            // When
            sendEvent("taxinvoice.processed", documentId1, event1);
            sendEvent("taxinvoice.processed", documentId2, event2);

            // Then — both notifications reach SENT status independently
            Map<String, Object> notification1 = awaitNotificationByDocumentId(documentId1);
            Map<String, Object> notification2 = awaitNotificationByDocumentId(documentId2);

            assertThat(notification1.get("status")).isEqualTo("SENT");
            assertThat(notification1.get("document_number")).isEqualTo(documentNumber1);
            assertThat(notification1.get("correlation_id")).isEqualTo(correlationId1);

            assertThat(notification2.get("status")).isEqualTo("SENT");
            assertThat(notification2.get("document_number")).isEqualTo(documentNumber2);
            assertThat(notification2.get("correlation_id")).isEqualTo(correlationId2);

            assertThat(notification1.get("id")).isNotEqualTo(notification2.get("id"));
        }
    }
}
