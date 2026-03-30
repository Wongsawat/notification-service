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

@DisplayName("SagaStepCompletedEvent Tests")
class SagaStepCompletedEventTest {

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
                    "eventId": "00000000-0000-0000-0000-000000000002",
                    "occurredAt": "2024-01-15T10:26:00Z",
                    "eventType": "SagaStepCompleted",
                    "version": 1,
                    "sagaId": "saga-step-123",
                    "correlationId": "corr-step-789",
                    "documentType": "INVOICE",
                    "completedStep": "document_validation",
                    "nextStep": "invoice_processing"
                }
                """;

            // Act
            SagaStepCompletedEvent event = objectMapper.readValue(json, SagaStepCompletedEvent.class);

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
            assertThat(event.getEventType()).isEqualTo("SagaStepCompleted");
            assertThat(event.getVersion()).isEqualTo(1);
            assertThat(event.getSagaId()).isEqualTo("saga-step-123");
            assertThat(event.getCorrelationId()).isEqualTo("corr-step-789");
            assertThat(event.getDocumentType()).isEqualTo("INVOICE");
            assertThat(event.getCompletedStep()).isEqualTo("document_validation");
            assertThat(event.getNextStep()).isEqualTo("invoice_processing");
        }

        @Test
        @DisplayName("Should deserialize JSON with null fields")
        void testDeserializeJsonWithNullFields() throws Exception {
            // Arrange
            String json = """
                {
                    "eventId": "00000000-0000-0000-0000-000000000002",
                    "occurredAt": "2024-01-15T10:26:00Z",
                    "eventType": "SagaStepCompleted",
                    "version": 1,
                    "sagaId": "saga-step-123",
                    "correlationId": null,
                    "documentType": null,
                    "completedStep": null,
                    "nextStep": null
                }
                """;

            // Act
            SagaStepCompletedEvent event = objectMapper.readValue(json, SagaStepCompletedEvent.class);

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
            assertThat(event.getCorrelationId()).isNull();
            assertThat(event.getCompletedStep()).isNull();
            assertThat(event.getNextStep()).isNull();
        }

        @Test
        @DisplayName("Should serialize to JSON correctly")
        void testSerializeToJson() throws Exception {
            // Arrange
            SagaStepCompletedEvent event = new SagaStepCompletedEvent(
                "saga-step-123",
                "corr-step-789",
                "INVOICE",
                "xml_signing",
                "pdf_generation"
            );

            // Act
            String json = objectMapper.writeValueAsString(event);

            // Assert
            assertThat(json).contains("\"sagaId\":\"saga-step-123\"");
            assertThat(json).contains("\"completedStep\":\"xml_signing\"");
            assertThat(json).contains("\"nextStep\":\"pdf_generation\"");
            assertThat(json).contains("\"eventId\"");
            assertThat(json).contains("\"occurredAt\"");
            assertThat(json).contains("\"version\":1");
        }

        @Test
        @DisplayName("Should create event with constructor")
        void testCreateEventWithConstructor() {
            // Act
            SagaStepCompletedEvent event = new SagaStepCompletedEvent(
                "saga-step-123",
                "corr-step-789",
                "TAX_INVOICE",
                "pdf_generation",
                "pdf_signing"
            );

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getSagaId()).isEqualTo("saga-step-123");
            assertThat(event.getCompletedStep()).isEqualTo("pdf_generation");
            assertThat(event.getNextStep()).isEqualTo("pdf_signing");
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getOccurredAt()).isNotNull();
            assertThat(event.getVersion()).isEqualTo(1);
        }
    }
}
