package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentReceivedTraceEvent local DTO tests")
class DocumentReceivedTraceEventTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @Test
    @DisplayName("Should deserialize producer JSON with all fields")
    void shouldDeserializeProducerJson() throws Exception {
        String json = """
            {
              "eventId": "123e4567-e89b-12d3-a456-426614174000",
              "occurredAt": "2024-01-01T00:00:00Z",
              "eventType": "DocumentReceivedTraceEvent",
              "version": 1,
              "documentId": "doc-123",
              "documentType": "TAX_INVOICE",
              "documentNumber": "INV-001",
              "correlationId": "corr-123",
              "status": "RECEIVED",
              "source": "API"
            }
            """;

        DocumentReceivedTraceEvent event = MAPPER.readValue(json, DocumentReceivedTraceEvent.class);

        assertThat(event.getDocumentId()).isEqualTo("doc-123");
        assertThat(event.getDocumentType()).isEqualTo("TAX_INVOICE");
        assertThat(event.getDocumentNumber()).isEqualTo("INV-001");
        assertThat(event.getCorrelationId()).isEqualTo("corr-123");
        assertThat(event.getStatus()).isEqualTo("RECEIVED");
        assertThat(event.getSource()).isEqualTo("API");
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Should deserialize producer JSON with null correlationId")
    void shouldDeserializeProducerJsonWithNullCorrelationId() throws Exception {
        String json = """
            {
              "eventId": "123e4567-e89b-12d3-a456-426614174000",
              "occurredAt": "2024-01-01T00:00:00Z",
              "eventType": "DocumentReceivedTraceEvent",
              "version": 1,
              "documentId": "doc-456",
              "documentType": "INVOICE",
              "documentNumber": "INV-456",
              "correlationId": null,
              "status": "VALIDATED",
              "source": "KAFKA"
            }
            """;

        DocumentReceivedTraceEvent event = MAPPER.readValue(json, DocumentReceivedTraceEvent.class);

        assertThat(event.getDocumentId()).isEqualTo("doc-456");
        assertThat(event.getDocumentType()).isEqualTo("INVOICE");
        assertThat(event.getStatus()).isEqualTo("VALIDATED");
        assertThat(event.getSource()).isEqualTo("KAFKA");
        assertThat(event.getCorrelationId()).isNull();
    }

    @Test
    @DisplayName("Should handle unknown fields gracefully via @JsonIgnoreProperties")
    void shouldIgnoreUnknownFields() throws Exception {
        String json = """
            {
              "eventId": "123e4567-e89b-12d3-a456-426614174000",
              "occurredAt": "2024-01-01T00:00:00Z",
              "eventType": "DocumentReceivedTraceEvent",
              "version": 1,
              "documentId": "doc-789",
              "documentType": "ABBREVIATED_TAX_INVOICE",
              "documentNumber": "ATI-789",
              "correlationId": "corr-789",
              "status": "INVALID",
              "source": "API",
              "futureField": "should be ignored"
            }
            """;

        DocumentReceivedTraceEvent event = MAPPER.readValue(json, DocumentReceivedTraceEvent.class);

        assertThat(event.getDocumentId()).isEqualTo("doc-789");
        assertThat(event.getStatus()).isEqualTo("INVALID");
    }
}
