package com.wpanther.notification.integration;

import com.wpanther.notification.infrastructure.messaging.InvoiceProcessedEvent;
import com.wpanther.notification.infrastructure.messaging.TaxInvoiceProcessedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
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
}
