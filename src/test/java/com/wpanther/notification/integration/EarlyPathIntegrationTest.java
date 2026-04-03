package com.wpanther.notification.integration;

import com.wpanther.notification.application.port.in.event.DocumentReceivedTraceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DisplayName("Early Path Integration Tests")
@Tag("integration")
@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")
class EarlyPathIntegrationTest extends AbstractKafkaConsumerTest {

    @BeforeEach
    void cleanIntakeStats() {
        testJdbcTemplate.execute("DELETE FROM document_intake_stats");
    }

    // -----------------------------------------------------------------------
    // Helper: wait for a row in document_intake_stats, then return it
    // -----------------------------------------------------------------------
    private Map<String, Object> awaitIntakeStatByDocumentId(String documentId) {
        await().atMost(30, TimeUnit.SECONDS)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> {
                   List<Map<String, Object>> rows = testJdbcTemplate.queryForList(
                       "SELECT * FROM document_intake_stats WHERE document_id = ?", documentId);
                   return !rows.isEmpty();
               });
        return testJdbcTemplate.queryForList(
            "SELECT * FROM document_intake_stats WHERE document_id = ?", documentId).get(0);
    }

    // -----------------------------------------------------------------------
    // Helper: build a DocumentReceivedTraceEvent for a given status
    // -----------------------------------------------------------------------
    private DocumentReceivedTraceEvent buildTraceEvent(String documentId,
                                                        String documentNumber,
                                                        String correlationId,
                                                        String status) {
        return new DocumentReceivedTraceEvent(
            UUID.randomUUID(),              // eventId
            Instant.now(),                  // occurredAt
            "DocumentReceivedTraceEvent",   // eventType
            1,                              // version
            null,                           // sagaId
            correlationId,                  // correlationId
            "API",                          // source
            "TRACE",                        // traceType
            null,                           // context
            documentId,                     // documentId
            "TAX_INVOICE",                  // documentType
            documentNumber,                 // documentNumber
            status                          // status
        );
    }

    // =======================================================================
    @Nested
    @DisplayName("DocumentIntakeEvents")
    class DocumentIntakeEvents {

        @Test
        @DisplayName("Should persist intake stat on RECEIVED status")
        void shouldPersistIntakeStatOnReceived() {
            // Given
            String documentId     = "DOC-" + UUID.randomUUID();
            String documentNumber = "TIV-RECV-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            DocumentReceivedTraceEvent event = buildTraceEvent(
                documentId, documentNumber, correlationId, "RECEIVED");

            // When
            sendEvent("trace.document.received", documentId, event);

            // Then — wait for row in document_intake_stats
            Map<String, Object> row = awaitIntakeStatByDocumentId(documentId);

            assertThat(row.get("document_id")).isEqualTo(documentId);
            assertThat(row.get("document_type")).isEqualTo("TAX_INVOICE");
            assertThat(row.get("document_number")).isEqualTo(documentNumber);
            assertThat(row.get("status")).isEqualTo("RECEIVED");
            assertThat(row.get("occurred_at")).isNotNull();
        }

        @Test
        @DisplayName("Should persist intake stat on VALIDATED status")
        void shouldPersistIntakeStatOnValidated() {
            // Given
            String documentId     = "DOC-" + UUID.randomUUID();
            String documentNumber = "TIV-VALID-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            DocumentReceivedTraceEvent event = buildTraceEvent(
                documentId, documentNumber, correlationId, "VALIDATED");

            // When
            sendEvent("trace.document.received", documentId, event);

            // Then
            Map<String, Object> row = awaitIntakeStatByDocumentId(documentId);

            assertThat(row.get("document_id")).isEqualTo(documentId);
            assertThat(row.get("document_number")).isEqualTo(documentNumber);
            assertThat(row.get("status")).isEqualTo("VALIDATED");
            assertThat(row.get("occurred_at")).isNotNull();
        }

        @Test
        @DisplayName("Should persist intake stat on FORWARDED status")
        void shouldPersistIntakeStatOnForwarded() {
            // Given
            String documentId     = "DOC-" + UUID.randomUUID();
            String documentNumber = "TIV-FWD-" + System.currentTimeMillis();
            String correlationId  = UUID.randomUUID().toString();

            DocumentReceivedTraceEvent event = buildTraceEvent(
                documentId, documentNumber, correlationId, "FORWARDED");

            // When
            sendEvent("trace.document.received", documentId, event);

            // Then
            Map<String, Object> row = awaitIntakeStatByDocumentId(documentId);

            assertThat(row.get("document_id")).isEqualTo(documentId);
            assertThat(row.get("document_number")).isEqualTo(documentNumber);
            assertThat(row.get("status")).isEqualTo("FORWARDED");
            assertThat(row.get("occurred_at")).isNotNull();
        }
    }
    // (OrchestratorLifecycleEvents group will be added in the next task)
}
