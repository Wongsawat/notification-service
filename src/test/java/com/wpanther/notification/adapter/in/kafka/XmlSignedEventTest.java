package com.wpanther.notification.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("XmlSignedEvent Tests")
class XmlSignedEventTest {

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
            String json = """
                {
                    "eventId": "00000000-0000-0000-0000-000000000001",
                    "occurredAt": "2024-01-15T10:30:00Z",
                    "eventType": "xml.signed",
                    "version": 1,
                    "invoiceId": "doc-123",
                    "invoiceNumber": "INV-001",
                    "documentType": "INVOICE",
                    "correlationId": "corr-456"
                }
                """;

            XmlSignedEvent event = objectMapper.readValue(json, XmlSignedEvent.class);

            assertThat(event).isNotNull();
            assertThat(event.getEventId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            assertThat(event.getEventType()).isEqualTo("xml.signed");
            assertThat(event.getVersion()).isEqualTo(1);
            assertThat(event.getInvoiceId()).isEqualTo("doc-123");
            assertThat(event.getInvoiceNumber()).isEqualTo("INV-001");
            assertThat(event.getDocumentType()).isEqualTo("INVOICE");
            assertThat(event.getCorrelationId()).isEqualTo("corr-456");
        }

        @Test
        @DisplayName("Should deserialize JSON with null optional fields")
        void testDeserializeJsonWithNullFields() throws Exception {
            String json = """
                {
                    "eventId": "00000000-0000-0000-0000-000000000001",
                    "occurredAt": "2024-01-15T10:30:00Z",
                    "eventType": "xml.signed",
                    "version": 1,
                    "invoiceId": "doc-123",
                    "invoiceNumber": null,
                    "documentType": null,
                    "correlationId": null
                }
                """;

            XmlSignedEvent event = objectMapper.readValue(json, XmlSignedEvent.class);

            assertThat(event).isNotNull();
            assertThat(event.getInvoiceId()).isEqualTo("doc-123");
            assertThat(event.getInvoiceNumber()).isNull();
            assertThat(event.getDocumentType()).isNull();
            assertThat(event.getCorrelationId()).isNull();
        }

        @Test
        @DisplayName("Should serialize to JSON correctly")
        void testSerializeToJson() throws Exception {
            XmlSignedEvent event = new XmlSignedEvent(
                "doc-789", "INV-002", "TAX_INVOICE", "corr-101"
            );

            String json = objectMapper.writeValueAsString(event);

            assertThat(json).contains("\"invoiceId\":\"doc-789\"");
            assertThat(json).contains("\"invoiceNumber\":\"INV-002\"");
            assertThat(json).contains("\"documentType\":\"TAX_INVOICE\"");
            assertThat(json).contains("\"correlationId\":\"corr-101\"");
            assertThat(json).contains("\"eventId\"");
            assertThat(json).contains("\"occurredAt\"");
            assertThat(json).contains("\"version\":1");
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create event with convenience constructor")
        void testConvenienceConstructor() {
            XmlSignedEvent event = new XmlSignedEvent(
                "doc-abc", "INV-100", "RECEIPT", "corr-xyz"
            );

            assertThat(event.getInvoiceId()).isEqualTo("doc-abc");
            assertThat(event.getInvoiceNumber()).isEqualTo("INV-100");
            assertThat(event.getDocumentType()).isEqualTo("RECEIPT");
            assertThat(event.getCorrelationId()).isEqualTo("corr-xyz");
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getOccurredAt()).isNotNull();
            assertThat(event.getVersion()).isEqualTo(1);
        }
    }
}
