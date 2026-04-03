package com.wpanther.notification.integration;

import com.wpanther.notification.application.port.in.event.DocumentReceivedTraceEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaCompletedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaFailedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaStartedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaStepCompletedEvent;
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
import java.util.concurrent.atomic.AtomicReference;

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
        AtomicReference<Map<String, Object>> result = new AtomicReference<>();
        await().atMost(30, TimeUnit.SECONDS)
               .pollInterval(1, TimeUnit.SECONDS)
               .until(() -> {
                   List<Map<String, Object>> rows = testJdbcTemplate.queryForList(
                       "SELECT * FROM document_intake_stats WHERE document_id = ?", documentId);
                   if (!rows.isEmpty()) {
                       result.set(rows.get(0));
                       return true;
                   }
                   return false;
               });
        return result.get();
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
            assertThat(row.get("correlation_id")).isEqualTo(correlationId);
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
            assertThat(row.get("document_type")).isEqualTo("TAX_INVOICE");
            assertThat(row.get("document_number")).isEqualTo(documentNumber);
            assertThat(row.get("status")).isEqualTo("VALIDATED");
            assertThat(row.get("occurred_at")).isNotNull();
            assertThat(row.get("correlation_id")).isEqualTo(correlationId);
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
            assertThat(row.get("document_type")).isEqualTo("TAX_INVOICE");
            assertThat(row.get("document_number")).isEqualTo(documentNumber);
            assertThat(row.get("status")).isEqualTo("FORWARDED");
            assertThat(row.get("occurred_at")).isNotNull();
            assertThat(row.get("correlation_id")).isEqualTo(correlationId);
        }
    }
    // =======================================================================
    @Nested
    @DisplayName("OrchestratorLifecycleEvents")
    class OrchestratorLifecycleEvents {

        @Test
        @DisplayName("Should log SagaStartedEvent without creating a notification")
        void shouldLogSagaStarted() {
            // Given
            String sagaId        = "SAGA-" + UUID.randomUUID();
            String correlationId = UUID.randomUUID().toString(); // required by event constructor; log-only route, no notification to assert

            SagaStartedEvent event = new SagaStartedEvent(
                sagaId, correlationId, "TAX_INVOICE",
                "DOC-" + UUID.randomUUID(), "PROCESS_TAX_INVOICE",
                "TIV-" + System.currentTimeMillis()
            );

            // When
            sendEvent("saga.lifecycle.started", sagaId, event);

            // Then — log only; notifications table must stay empty for 15 s
            assertNoNotificationCreatedAfterWait();
        }

        @Test
        @DisplayName("Should log SagaStepCompletedEvent without creating a notification")
        void shouldLogSagaStepCompleted() {
            // Given
            String sagaId        = "SAGA-" + UUID.randomUUID();
            String correlationId = UUID.randomUUID().toString(); // required by event constructor; log-only route, no notification to assert

            SagaStepCompletedEvent event = new SagaStepCompletedEvent(
                sagaId, correlationId, "TAX_INVOICE",
                "PROCESS_TAX_INVOICE", "SIGN_XML"
            );

            // When
            sendEvent("saga.lifecycle.step-completed", sagaId, event);

            // Then — log only; notifications table must stay empty for 15 s
            assertNoNotificationCreatedAfterWait();
        }

        @Test
        @DisplayName("Should create email notification on SagaCompletedEvent")
        void shouldCreateNotificationOnSagaCompleted() {
            // Given
            String sagaId         = "SAGA-" + UUID.randomUUID();
            String correlationId  = UUID.randomUUID().toString();
            String documentType   = "TAX_INVOICE";
            String documentId     = "DOC-" + UUID.randomUUID();
            String documentNumber = "TIV-COMP-" + System.currentTimeMillis();
            Integer stepsExecuted = 7;
            Instant startedAt     = Instant.now().minusSeconds(90);
            Instant completedAt   = Instant.now();
            Long durationMs       = 90000L;

            SagaCompletedEvent event = new SagaCompletedEvent(
                sagaId, correlationId, documentType, documentId, documentNumber,
                stepsExecuted, startedAt, completedAt, durationMs
            );

            // When
            sendEvent("saga.lifecycle.completed", documentId, event);

            // Then — notification must reach SENT status
            Map<String, Object> notification = awaitNotificationByDocumentId(documentId);

            assertThat(notification.get("type")).isEqualTo("SAGA_COMPLETED");
            assertThat(notification.get("channel")).isEqualTo("EMAIL");
            assertThat(notification.get("status")).isEqualTo("SENT");
            assertThat(notification.get("template_name")).isEqualTo("saga-completed");
            assertThat(notification.get("recipient")).isEqualTo("test-integration@example.com");
            assertThat(notification.get("document_id")).isEqualTo(documentId);
            assertThat(notification.get("document_number")).isEqualTo(documentNumber);
            assertThat(notification.get("correlation_id")).isEqualTo(correlationId);
            assertThat((String) notification.get("subject")).contains("Saga Completed");
            assertThat((String) notification.get("subject")).contains(documentNumber);

            String templateVars = (String) notification.get("template_variables");
            assertThat(templateVars).contains(sagaId);
            assertThat(templateVars).contains(documentId);
            assertThat(templateVars).contains(documentNumber);
            assertThat(templateVars).contains(documentType);
            assertThat(templateVars).contains(stepsExecuted.toString());
            assertThat(templateVars).contains(durationMs.toString());
            assertThat(templateVars).contains("durationSec");
        }

        @Test
        @DisplayName("Should create urgent email notification on SagaFailedEvent")
        void shouldCreateUrgentNotificationOnSagaFailed() {
            // Given
            String sagaId         = "SAGA-" + UUID.randomUUID();
            String correlationId  = UUID.randomUUID().toString();
            String documentType   = "TAX_INVOICE";
            String documentId     = "DOC-" + UUID.randomUUID();
            String documentNumber = "TIV-FAIL-" + System.currentTimeMillis();
            String failedStep     = "SIGN_XML";
            String errorMessage   = "Connection timeout to signing service";
            Integer retryCount    = 3;
            Boolean compensationInitiated = true;
            Instant startedAt     = Instant.now().minusSeconds(45);
            Instant failedAt      = Instant.now();
            Long durationMs       = 45000L;

            SagaFailedEvent event = new SagaFailedEvent(
                sagaId, correlationId, documentType, documentId, documentNumber,
                failedStep, errorMessage, retryCount, compensationInitiated,
                startedAt, failedAt, durationMs
            );

            // When
            sendEvent("saga.lifecycle.failed", documentId, event);

            // Then — notification must reach SENT status
            Map<String, Object> notification = awaitNotificationByDocumentId(documentId);

            assertThat(notification.get("type")).isEqualTo("SAGA_FAILED");
            assertThat(notification.get("channel")).isEqualTo("EMAIL");
            assertThat(notification.get("status")).isEqualTo("SENT");
            assertThat(notification.get("template_name")).isEqualTo("saga-failed");
            assertThat(notification.get("recipient")).isEqualTo("test-integration@example.com");
            assertThat(notification.get("document_id")).isEqualTo(documentId);
            assertThat(notification.get("document_number")).isEqualTo(documentNumber);
            assertThat(notification.get("correlation_id")).isEqualTo(correlationId);
            assertThat((String) notification.get("subject")).contains("URGENT: Saga Failed");
            assertThat((String) notification.get("subject")).contains(documentNumber);

            String templateVars = (String) notification.get("template_variables");
            assertThat(templateVars).contains(sagaId);
            assertThat(templateVars).contains(documentId);
            assertThat(templateVars).contains(failedStep);
            assertThat(templateVars).contains(errorMessage);
            assertThat(templateVars).contains(retryCount.toString());
            assertThat(templateVars).contains("true");  // compensationInitiated
            assertThat(templateVars).contains("failedAt");
        }
    }
}
