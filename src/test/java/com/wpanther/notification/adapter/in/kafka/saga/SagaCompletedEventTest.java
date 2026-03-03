package com.wpanther.notification.adapter.in.kafka.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SagaCompletedEvent Tests")
class SagaCompletedEventTest {

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
                    "eventId": "00000000-0000-0000-0000-000000000003",
                    "occurredAt": "2024-01-15T10:30:00Z",
                    "eventType": "SagaCompleted",
                    "version": 1,
                    "sagaId": "saga-abc-123",
                    "correlationId": "corr-xyz-789",
                    "documentType": "INVOICE",
                    "documentId": "doc-inv-001",
                    "invoiceNumber": "INV-2024-001",
                    "stepsExecuted": 5,
                    "startedAt": "2024-01-15T10:25:00Z",
                    "completedAt": "2024-01-15T10:30:00Z",
                    "durationMs": 300000
                }
                """;

            // Act
            SagaCompletedEvent event = objectMapper.readValue(json, SagaCompletedEvent.class);

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000003"));
            assertThat(event.getEventType()).isEqualTo("SagaCompleted");
            assertThat(event.getVersion()).isEqualTo(1);
            assertThat(event.getSagaId()).isEqualTo("saga-abc-123");
            assertThat(event.getCorrelationId()).isEqualTo("corr-xyz-789");
            assertThat(event.getDocumentType()).isEqualTo("INVOICE");
            assertThat(event.getDocumentId()).isEqualTo("doc-inv-001");
            assertThat(event.getInvoiceNumber()).isEqualTo("INV-2024-001");
            assertThat(event.getStepsExecuted()).isEqualTo(5);
            assertThat(event.getDurationMs()).isEqualTo(300000L);
        }

        @Test
        @DisplayName("Should deserialize JSON with null fields")
        void testDeserializeJsonWithNullFields() throws Exception {
            // Arrange
            String json = """
                {
                    "eventId": "00000000-0000-0000-0000-000000000003",
                    "occurredAt": "2024-01-15T10:30:00Z",
                    "eventType": "SagaCompleted",
                    "version": 1,
                    "sagaId": "saga-abc-123",
                    "correlationId": null,
                    "documentType": null,
                    "documentId": null,
                    "invoiceNumber": null,
                    "stepsExecuted": null,
                    "startedAt": null,
                    "completedAt": null,
                    "durationMs": null
                }
                """;

            // Act
            SagaCompletedEvent event = objectMapper.readValue(json, SagaCompletedEvent.class);

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000003"));
            assertThat(event.getCorrelationId()).isNull();
            assertThat(event.getDocumentType()).isNull();
            assertThat(event.getInvoiceNumber()).isNull();
            assertThat(event.getStepsExecuted()).isNull();
        }

        @Test
        @DisplayName("Should serialize to JSON correctly")
        void testSerializeToJson() throws Exception {
            // Arrange
            SagaCompletedEvent event = new SagaCompletedEvent(
                "saga-abc-123",
                "corr-xyz-789",
                "TAX_INVOICE",
                "doc-tax-001",
                "TAX-2024-001",
                6,
                Instant.parse("2024-01-15T10:25:00Z"),
                Instant.parse("2024-01-15T10:30:00Z"),
                300000L
            );

            // Act
            String json = objectMapper.writeValueAsString(event);

            // Assert
            assertThat(json).contains("\"sagaId\":\"saga-abc-123\"");
            assertThat(json).contains("\"invoiceNumber\":\"TAX-2024-001\"");
            assertThat(json).contains("\"stepsExecuted\":6");
            assertThat(json).contains("\"eventId\"");
            assertThat(json).contains("\"occurredAt\"");
            assertThat(json).contains("\"version\":1");
        }

        @Test
        @DisplayName("Should create event with constructor")
        void testCreateEventWithConstructor() {
            // Act
            SagaCompletedEvent event = new SagaCompletedEvent(
                "saga-abc-123",
                "corr-xyz-789",
                "INVOICE",
                "doc-inv-001",
                "INV-2024-001",
                5,
                Instant.parse("2024-01-15T10:25:00Z"),
                Instant.parse("2024-01-15T10:30:00Z"),
                300000L
            );

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getSagaId()).isEqualTo("saga-abc-123");
            assertThat(event.getInvoiceNumber()).isEqualTo("INV-2024-001");
            assertThat(event.getStepsExecuted()).isEqualTo(5);
            assertThat(event.getDurationMs()).isEqualTo(300000L);
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getOccurredAt()).isNotNull();
            assertThat(event.getVersion()).isEqualTo(1);
        }
    }
}
