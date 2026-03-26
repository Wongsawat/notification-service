package com.wpanther.notification.application.port.in.event.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SagaFailedEvent Tests")
class SagaFailedEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Nested
    @DisplayName("JSON Serialization/Deserialization Tests")
    class JsonSerializationTests {

        @Test
        @DisplayName("Should deserialize JSON with all fields")
        void testDeserializeJsonWithAllFields() throws Exception {
            // Arrange
            String json = """
                {
                    "eventId": "00000000-0000-0000-0000-000000000004",
                    "occurredAt": "2024-01-15T10:35:00Z",
                    "eventType": "SagaFailed",
                    "version": 1,
                    "sagaId": "saga-failed-123",
                    "correlationId": "corr-failed-789",
                    "documentType": "TAX_INVOICE",
                    "documentId": "doc-tax-001",
                    "invoiceNumber": "TAX-2024-001",
                    "failedStep": "xml_signing",
                    "errorMessage": "Failed to connect to signing service",
                    "retryCount": 2,
                    "compensationInitiated": true,
                    "startedAt": "2024-01-15T10:30:00Z",
                    "failedAt": "2024-01-15T10:35:00Z",
                    "durationMs": 300000
                }
                """;

            // Act
            SagaFailedEvent event = objectMapper.readValue(json, SagaFailedEvent.class);

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000004"));
            assertThat(event.getEventType()).isEqualTo("SagaFailed");
            assertThat(event.getVersion()).isEqualTo(1);
            assertThat(event.getSagaId()).isEqualTo("saga-failed-123");
            assertThat(event.getCorrelationId()).isEqualTo("corr-failed-789");
            assertThat(event.getDocumentType()).isEqualTo("TAX_INVOICE");
            assertThat(event.getDocumentId()).isEqualTo("doc-tax-001");
            assertThat(event.getInvoiceNumber()).isEqualTo("TAX-2024-001");
            assertThat(event.getFailedStep()).isEqualTo("xml_signing");
            assertThat(event.getErrorMessage()).isEqualTo("Failed to connect to signing service");
            assertThat(event.getRetryCount()).isEqualTo(2);
            assertThat(event.getCompensationInitiated()).isTrue();
            assertThat(event.getDurationMs()).isEqualTo(300000L);
        }

        @Test
        @DisplayName("Should deserialize JSON with null fields")
        void testDeserializeJsonWithNullFields() throws Exception {
            // Arrange
            String json = """
                {
                    "eventId": "00000000-0000-0000-0000-000000000004",
                    "occurredAt": "2024-01-15T10:35:00Z",
                    "eventType": "SagaFailed",
                    "version": 1,
                    "sagaId": "saga-failed-123",
                    "correlationId": null,
                    "documentType": null,
                    "documentId": null,
                    "invoiceNumber": null,
                    "failedStep": null,
                    "errorMessage": null,
                    "retryCount": null,
                    "compensationInitiated": null,
                    "startedAt": null,
                    "failedAt": null,
                    "durationMs": null
                }
                """;

            // Act
            SagaFailedEvent event = objectMapper.readValue(json, SagaFailedEvent.class);

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000004"));
            assertThat(event.getCorrelationId()).isNull();
            assertThat(event.getFailedStep()).isNull();
            assertThat(event.getErrorMessage()).isNull();
            assertThat(event.getCompensationInitiated()).isNull();
        }

        @Test
        @DisplayName("Should serialize to JSON correctly")
        void testSerializeToJson() throws Exception {
            // Arrange
            SagaFailedEvent event = new SagaFailedEvent(
                "saga-failed-123",
                "corr-failed-789",
                "INVOICE",
                "doc-inv-001",
                "INV-2024-001",
                "pdf_generation",
                "PDF generation timeout",
                1,
                false,
                Instant.parse("2024-01-15T10:30:00Z"),
                Instant.parse("2024-01-15T10:35:00Z"),
                150000L
            );

            // Act
            String json = objectMapper.writeValueAsString(event);

            // Assert
            assertThat(json).contains("\"sagaId\":\"saga-failed-123\"");
            assertThat(json).contains("\"failedStep\":\"pdf_generation\"");
            assertThat(json).contains("\"errorMessage\":\"PDF generation timeout\"");
            assertThat(json).contains("\"retryCount\":1");
            assertThat(json).contains("\"compensationInitiated\":false");
            assertThat(json).contains("\"eventId\"");
            assertThat(json).contains("\"occurredAt\"");
            assertThat(json).contains("\"version\":1");
        }

        @Test
        @DisplayName("Should create event with constructor")
        void testCreateEventWithConstructor() {
            // Act
            SagaFailedEvent event = new SagaFailedEvent(
                "saga-failed-123",
                "corr-failed-789",
                "TAX_INVOICE",
                "doc-tax-001",
                "TAX-2024-001",
                "xml_signing",
                "Signing service unavailable",
                3,
                true,
                Instant.parse("2024-01-15T10:30:00Z"),
                Instant.parse("2024-01-15T10:35:00Z"),
                450000L
            );

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getSagaId()).isEqualTo("saga-failed-123");
            assertThat(event.getFailedStep()).isEqualTo("xml_signing");
            assertThat(event.getErrorMessage()).isEqualTo("Signing service unavailable");
            assertThat(event.getCompensationInitiated()).isTrue();
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getOccurredAt()).isNotNull();
            assertThat(event.getVersion()).isEqualTo(1);
        }
    }
}
