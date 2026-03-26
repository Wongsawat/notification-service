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

@DisplayName("SagaStartedEvent Tests")
class SagaStartedEventTest {

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
                    "eventId": "00000000-0000-0000-0000-000000000001",
                    "occurredAt": "2024-01-15T10:20:00.000000000Z",
                    "eventType": "SagaStarted",
                    "version": 1,
                    "sagaId": "saga-start-123",
                    "correlationId": "corr-start-789",
                    "documentType": "RECEIPT",
                    "documentId": "doc-rcpt-001",
                    "currentStep": "document_intake",
                    "invoiceNumber": "RCT-2024-001",
                    "startedAt": "2024-01-15T10:20:00.000000000Z"
                }
                """;

            // Act
            SagaStartedEvent event = objectMapper.readValue(json, SagaStartedEvent.class);

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            assertThat(event.getEventType()).isEqualTo("SagaStarted");
            assertThat(event.getVersion()).isEqualTo(1);
            assertThat(event.getSagaId()).isEqualTo("saga-start-123");
            assertThat(event.getCorrelationId()).isEqualTo("corr-start-789");
            assertThat(event.getDocumentType()).isEqualTo("RECEIPT");
            assertThat(event.getDocumentId()).isEqualTo("doc-rcpt-001");
            assertThat(event.getCurrentStep()).isEqualTo("document_intake");
            assertThat(event.getInvoiceNumber()).isEqualTo("RCT-2024-001");
        }

        @Test
        @DisplayName("Should deserialize JSON with null fields")
        void testDeserializeJsonWithNullFields() throws Exception {
            // Arrange
            String json = """
                {
                    "eventId": "00000000-0000-0000-0000-000000000001",
                    "occurredAt": "2024-01-15T10:20:00.000000000Z",
                    "eventType": "SagaStarted",
                    "version": 1,
                    "sagaId": "saga-start-123",
                    "correlationId": null,
                    "documentType": null,
                    "documentId": null,
                    "currentStep": null,
                    "invoiceNumber": null,
                    "startedAt": null
                }
                """;

            // Act
            SagaStartedEvent event = objectMapper.readValue(json, SagaStartedEvent.class);

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            assertThat(event.getCorrelationId()).isNull();
            assertThat(event.getCurrentStep()).isNull();
            assertThat(event.getInvoiceNumber()).isNull();
        }

        @Test
        @DisplayName("Should serialize to JSON correctly")
        void testSerializeToJson() throws Exception {
            // Arrange
            SagaStartedEvent event = new SagaStartedEvent(
                "saga-start-123",
                "corr-start-789",
                "INVOICE",
                "doc-inv-001",
                "invoice_processing",
                "INV-2024-002",
                Instant.parse("2024-01-15T10:20:00Z")
            );

            // Act
            String json = objectMapper.writeValueAsString(event);

            // Assert
            assertThat(json).contains("\"sagaId\":\"saga-start-123\"");
            assertThat(json).contains("\"currentStep\":\"invoice_processing\"");
            assertThat(json).contains("\"invoiceNumber\":\"INV-2024-002\"");
            assertThat(json).contains("\"eventId\"");
            assertThat(json).contains("\"occurredAt\"");
            assertThat(json).contains("\"version\":1");
        }

        @Test
        @DisplayName("Should create event with constructor")
        void testCreateEventWithConstructor() {
            // Act
            SagaStartedEvent event = new SagaStartedEvent(
                "saga-start-123",
                "corr-start-789",
                "TAX_INVOICE",
                "doc-tax-001",
                "validation",
                "TAX-2024-001",
                Instant.now()
            );

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getSagaId()).isEqualTo("saga-start-123");
            assertThat(event.getCurrentStep()).isEqualTo("validation");
            assertThat(event.getInvoiceNumber()).isEqualTo("TAX-2024-001");
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getOccurredAt()).isNotNull();
            assertThat(event.getVersion()).isEqualTo(1);
        }
    }
}
