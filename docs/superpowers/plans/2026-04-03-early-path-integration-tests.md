# Early Path Integration Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 7 integration tests in `EarlyPathIntegrationTest` that verify notification-service correctly consumes `trace.document.received` (3 tests) and all 4 `saga.lifecycle.*` topics (4 tests).

**Architecture:** A new test class `EarlyPathIntegrationTest` extends `AbstractKafkaConsumerTest` to inherit all test infrastructure (Spring context, mocked senders, `testJdbcTemplate`, `testKafkaTemplate`, Awaitility helpers). The class adds a local `awaitIntakeStatByDocumentId()` helper and a `@BeforeEach` that also cleans `document_intake_stats`. No new config classes or production code are needed — only a one-line YAML fix and the test file itself.

**Tech Stack:** Java 21, JUnit 5 (`@Nested`, `@DisplayName`), Awaitility 4.x, AssertJ, Spring Boot Test, Apache Camel 4.14.4, Kafka on `localhost:9093`, PostgreSQL on `localhost:5433`

---

## Prerequisites

Containers must be running before executing any integration test:

```bash
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh
```

---

## File Map

| Action | File |
|--------|------|
| Modify | `src/test/resources/application-consumer-test.yml` — add `trace-document-received` topic |
| Create | `src/test/java/com/wpanther/notification/integration/EarlyPathIntegrationTest.java` |

---

## Task 1: Fix missing topic in test config

**Files:**
- Modify: `src/test/resources/application-consumer-test.yml`

- [ ] **Step 1: Add `trace-document-received` under `kafka.topics`**

Open `src/test/resources/application-consumer-test.yml`. The `kafka.topics` block currently ends at `saga-lifecycle-failed`. Add one line:

```yaml
kafka:
  topics:
    invoice-processed: invoice.processed
    taxinvoice-processed: taxinvoice.processed
    pdf-generated: pdf.generated.invoice
    pdf-generated-tax-invoice: pdf.generated.tax-invoice
    pdf-signed: pdf.signed
    ebms-sent: ebms.sent
    notification-dlq: notification.dlq
    saga-lifecycle-started: saga.lifecycle.started
    saga-lifecycle-step-completed: saga.lifecycle.step-completed
    saga-lifecycle-completed: saga.lifecycle.completed
    saga-lifecycle-failed: saga.lifecycle.failed
    trace-document-received: trace.document.received   # ← add this line
```

- [ ] **Step 2: Verify the app starts with the new config**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/notification-service
mvn test -Dtest=NotificationServiceApplicationTest
```

Expected: BUILD SUCCESS (application context loads cleanly).

- [ ] **Step 3: Commit**

```bash
git add src/test/resources/application-consumer-test.yml
git commit -m "test: add trace-document-received topic to consumer-test profile"
```

---

## Task 2: Create `EarlyPathIntegrationTest` — class skeleton + DocumentIntakeEvents group

**Files:**
- Create: `src/test/java/com/wpanther/notification/integration/EarlyPathIntegrationTest.java`

- [ ] **Step 1: Create the file with class skeleton, `@BeforeEach`, and all 3 DocumentIntakeEvents tests**

```java
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
    // (OrchestratorLifecycleEvents group added in Task 3)
}
```

- [ ] **Step 2: Verify the file compiles**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/notification-service
mvn compile -q
```

Expected: BUILD SUCCESS with no compilation errors.

- [ ] **Step 3: Run DocumentIntakeEvents tests (containers must be running)**

```bash
mvn test -Pintegration -Dtest="EarlyPathIntegrationTest\$DocumentIntakeEvents"
```

Expected: 3 tests pass (RECEIVED, VALIDATED, FORWARDED each persist a row).

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/wpanther/notification/integration/EarlyPathIntegrationTest.java
git commit -m "test: add EarlyPathIntegrationTest with DocumentIntakeEvents group (3 tests)"
```

---

## Task 3: Add `OrchestratorLifecycleEvents` nested group (4 tests)

**Files:**
- Modify: `src/test/java/com/wpanther/notification/integration/EarlyPathIntegrationTest.java`

- [ ] **Step 1: Replace the closing `}` at the end of the file with the full OrchestratorLifecycleEvents group**

Remove the placeholder comment line `// (OrchestratorLifecycleEvents group added in Task 3)` and add the full nested class before the outer closing brace:

```java
    // =======================================================================
    @Nested
    @DisplayName("OrchestratorLifecycleEvents")
    class OrchestratorLifecycleEvents {

        @Test
        @DisplayName("Should log SagaStartedEvent without creating a notification")
        void shouldLogSagaStarted() {
            // Given
            String sagaId        = "SAGA-" + UUID.randomUUID();
            String correlationId = UUID.randomUUID().toString();

            com.wpanther.notification.application.port.in.event.saga.SagaStartedEvent event =
                new com.wpanther.notification.application.port.in.event.saga.SagaStartedEvent(
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
            String correlationId = UUID.randomUUID().toString();

            com.wpanther.notification.application.port.in.event.saga.SagaStepCompletedEvent event =
                new com.wpanther.notification.application.port.in.event.saga.SagaStepCompletedEvent(
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
            String sagaId        = "SAGA-" + UUID.randomUUID();
            String correlationId = UUID.randomUUID().toString();
            String documentType  = "TAX_INVOICE";
            String documentId    = "DOC-" + UUID.randomUUID();
            String documentNumber = "TIV-COMP-" + System.currentTimeMillis();
            Integer stepsExecuted = 7;
            Instant startedAt   = Instant.now().minusSeconds(90);
            Instant completedAt = Instant.now();
            Long durationMs     = 90000L;

            com.wpanther.notification.application.port.in.event.saga.SagaCompletedEvent event =
                new com.wpanther.notification.application.port.in.event.saga.SagaCompletedEvent(
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

            String templateVars = (String) notification.get("template_variables");
            assertThat(templateVars).contains(sagaId);
            assertThat(templateVars).contains(documentId);
            assertThat(templateVars).contains(documentNumber);
            assertThat(templateVars).contains(documentType);
            assertThat(templateVars).contains(stepsExecuted.toString());
            assertThat(templateVars).contains(durationMs.toString());
        }

        @Test
        @DisplayName("Should create urgent email notification on SagaFailedEvent")
        void shouldCreateUrgentNotificationOnSagaFailed() {
            // Given
            String sagaId        = "SAGA-" + UUID.randomUUID();
            String correlationId = UUID.randomUUID().toString();
            String documentType  = "TAX_INVOICE";
            String documentId    = "DOC-" + UUID.randomUUID();
            String documentNumber = "TIV-FAIL-" + System.currentTimeMillis();
            String failedStep    = "SIGN_XML";
            String errorMessage  = "Connection timeout to signing service";
            Integer retryCount   = 3;
            Boolean compensationInitiated = true;
            Instant startedAt   = Instant.now().minusSeconds(45);
            Instant failedAt    = Instant.now();
            Long durationMs     = 45000L;

            com.wpanther.notification.application.port.in.event.saga.SagaFailedEvent event =
                new com.wpanther.notification.application.port.in.event.saga.SagaFailedEvent(
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
            assertThat((String) notification.get("subject")).contains("URGENT");

            String templateVars = (String) notification.get("template_variables");
            assertThat(templateVars).contains(sagaId);
            assertThat(templateVars).contains(documentId);
            assertThat(templateVars).contains(failedStep);
            assertThat(templateVars).contains(errorMessage);
            assertThat(templateVars).contains(retryCount.toString());
            assertThat(templateVars).contains("true");  // compensationInitiated
        }
    }
```

- [ ] **Step 2: Add saga event imports at the top of the file**

The saga event classes are referenced with fully-qualified names in the test bodies above, which is self-documenting but verbose. Either style compiles. If you prefer short imports, add these four lines to the import block at the top of the file:

```java
import com.wpanther.notification.application.port.in.event.saga.SagaCompletedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaFailedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaStartedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaStepCompletedEvent;
```

Then remove the `com.wpanther.notification.application.port.in.event.saga.` prefix from the four constructor calls in the test methods. Confirm the file compiles:

```bash
mvn compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Run all 7 EarlyPathIntegrationTest tests (containers must be running)**

```bash
mvn test -Pintegration -Dtest=EarlyPathIntegrationTest
```

Expected: 7 tests pass — 3 DocumentIntakeEvents + 4 OrchestratorLifecycleEvents.

If any test fails, check:
- `shouldLogSaga*` failures → the route is accidentally creating a notification. Check `NotificationEventRoutes` saga-started/step-completed handlers to confirm they only log.
- `shouldPersistIntakeStat*` failures → confirm `trace-document-received` is in `application-consumer-test.yml` (Task 1).
- `shouldCreate*` failures → run individual test with `-Dtest=EarlyPathIntegrationTest#shouldCreateNotificationOnSagaCompleted` and check logs for Camel deserialization errors.

- [ ] **Step 4: Run both integration test classes together to confirm no interference**

```bash
mvn test -Pintegration -Dtest="KafkaConsumerIntegrationTest,EarlyPathIntegrationTest"
```

Expected: All tests (7 existing + 7 new = 14 total) pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/wpanther/notification/integration/EarlyPathIntegrationTest.java
git commit -m "test: add OrchestratorLifecycleEvents group to EarlyPathIntegrationTest (4 tests)"
```
