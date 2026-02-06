package com.wpanther.notification.infrastructure.messaging.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
                    "eventId": "evt-step-001",
                    "occurredAt": "2024-01-15T10:26:00Z",
                    "eventType": "SagaStepCompleted",
                    "version": 1,
                    "sagaId": "saga-step-123",
                    "correlationId": "corr-step-789",
                    "documentType": "INVOICE",
                    "documentId": "doc-step-001",
                    "completedStep": "document_validation",
                    "nextStep": "invoice_processing",
                    "invoiceNumber": "INV-2024-004",
                    "stepDurationMs": 5000
                }
                """;

            // Act
            SagaStepCompletedEvent event = objectMapper.readValue(json, SagaStepCompletedEvent.class);

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getEventId()).isEqualTo("evt-step-001");
            assertThat(event.getEventType()).isEqualTo("SagaStepCompleted");
            assertThat(event.getVersion()).isEqualTo(1);
            assertThat(event.getSagaId()).isEqualTo("saga-step-123");
            assertThat(event.getCorrelationId()).isEqualTo("corr-step-789");
            assertThat(event.getDocumentType()).isEqualTo("INVOICE");
            assertThat(event.getDocumentId()).isEqualTo("doc-step-001");
            assertThat(event.getCompletedStep()).isEqualTo("document_validation");
            assertThat(event.getNextStep()).isEqualTo("invoice_processing");
            assertThat(event.getInvoiceNumber()).isEqualTo("INV-2024-004");
            assertThat(event.getStepDurationMs()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("Should deserialize JSON with null fields")
        void testDeserializeJsonWithNullFields() throws Exception {
            // Arrange
            String json = """
                {
                    "eventId": "evt-step-001",
                    "occurredAt": "2024-01-15T10:26:00Z",
                    "eventType": "SagaStepCompleted",
                    "version": 1,
                    "sagaId": "saga-step-123",
                    "correlationId": null,
                    "documentType": null,
                    "documentId": null,
                    "completedStep": null,
                    "nextStep": null,
                    "invoiceNumber": null,
                    "stepDurationMs": null
                }
                """;

            // Act
            SagaStepCompletedEvent event = objectMapper.readValue(json, SagaStepCompletedEvent.class);

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.getEventId()).isEqualTo("evt-step-001");
            assertThat(event.getCorrelationId()).isNull();
            assertThat(event.getCompletedStep()).isNull();
            assertThat(event.getNextStep()).isNull();
            assertThat(event.getStepDurationMs()).isNull();
        }

        @Test
        @DisplayName("Should serialize to JSON correctly")
        void testSerializeToJson() throws Exception {
            // Arrange
            SagaStepCompletedEvent event = SagaStepCompletedEvent.builder()
                .eventId("evt-step-001")
                .eventType("SagaStepCompleted")
                .sagaId("saga-step-123")
                .completedStep("xml_signing")
                .nextStep("pdf_generation")
                .invoiceNumber("INV-2024-005")
                .stepDurationMs(10000L)
                .build();

            // Act
            String json = objectMapper.writeValueAsString(event);

            // Assert
            assertThat(json).contains("\"eventId\":\"evt-step-001\"");
            assertThat(json).contains("\"completedStep\":\"xml_signing\"");
            assertThat(json).contains("\"nextStep\":\"pdf_generation\"");
            assertThat(json).contains("\"stepDurationMs\":10000");
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build event with all fields")
        void testBuildEventWithAllFields() {
            // Act
            SagaStepCompletedEvent event = SagaStepCompletedEvent.builder()
                .eventId("evt-step-001")
                .eventType("SagaStepCompleted")
                .sagaId("saga-step-123")
                .completedStep("pdf_generation")
                .nextStep("pdf_signing")
                .invoiceNumber("INV-2024-006")
                .stepDurationMs(15000L)
                .build();

            // Assert
            assertThat(event.getEventId()).isEqualTo("evt-step-001");
            assertThat(event.getCompletedStep()).isEqualTo("pdf_generation");
            assertThat(event.getNextStep()).isEqualTo("pdf_signing");
            assertThat(event.getStepDurationMs()).isEqualTo(15000L);
        }

        @Test
        @DisplayName("Should create event with no-args constructor")
        void testCreateEventWithNoArgsConstructor() {
            // Act
            SagaStepCompletedEvent event = new SagaStepCompletedEvent();

            // Assert
            assertThat(event).isNotNull();
        }
    }
}
