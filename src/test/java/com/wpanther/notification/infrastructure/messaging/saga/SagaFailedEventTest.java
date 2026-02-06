package com.wpanther.notification.infrastructure.messaging.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
                    "eventId": "evt-456",
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
            assertThat(event.getEventId()).isEqualTo("evt-456");
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
                    "eventId": "evt-456",
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
            assertThat(event.getEventId()).isEqualTo("evt-456");
            assertThat(event.getCorrelationId()).isNull();
            assertThat(event.getFailedStep()).isNull();
            assertThat(event.getErrorMessage()).isNull();
            assertThat(event.getCompensationInitiated()).isNull();
        }

        @Test
        @DisplayName("Should serialize to JSON correctly")
        void testSerializeToJson() throws Exception {
            // Arrange
            SagaFailedEvent event = SagaFailedEvent.builder()
                .eventId("evt-456")
                .eventType("SagaFailed")
                .sagaId("saga-failed-123")
                .failedStep("pdf_generation")
                .errorMessage("PDF generation timeout")
                .retryCount(1)
                .compensationInitiated(false)
                .durationMs(150000L)
                .build();

            // Act
            String json = objectMapper.writeValueAsString(event);

            // Assert
            assertThat(json).contains("\"eventId\":\"evt-456\"");
            assertThat(json).contains("\"failedStep\":\"pdf_generation\"");
            assertThat(json).contains("\"errorMessage\":\"PDF generation timeout\"");
            assertThat(json).contains("\"retryCount\":1");
            assertThat(json).contains("\"compensationInitiated\":false");
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build event with all fields")
        void testBuildEventWithAllFields() {
            // Act
            SagaFailedEvent event = SagaFailedEvent.builder()
                .eventId("evt-456")
                .eventType("SagaFailed")
                .sagaId("saga-failed-123")
                .failedStep("xml_signing")
                .errorMessage("Signing service unavailable")
                .retryCount(3)
                .compensationInitiated(true)
                .durationMs(450000L)
                .build();

            // Assert
            assertThat(event.getEventId()).isEqualTo("evt-456");
            assertThat(event.getFailedStep()).isEqualTo("xml_signing");
            assertThat(event.getErrorMessage()).isEqualTo("Signing service unavailable");
            assertThat(event.getCompensationInitiated()).isTrue();
        }

        @Test
        @DisplayName("Should create event with no-args constructor")
        void testCreateEventWithNoArgsConstructor() {
            // Act
            SagaFailedEvent event = new SagaFailedEvent();

            // Assert
            assertThat(event).isNotNull();
        }
    }
}
