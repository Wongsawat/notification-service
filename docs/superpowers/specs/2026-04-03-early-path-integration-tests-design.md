# Early Path Integration Tests — Design Spec

**Date:** 2026-04-03
**Service:** notification-service
**Scope:** Integration tests for Kafka topic consumption from document-intake-service and orchestrator-service (early path only)

---

## Context

The notification-service is a **pure Kafka consumer** — it publishes nothing back to Kafka and has no outbox infrastructure. All consumption is handled by Apache Camel `RouteBuilder` routes in `NotificationEventRoutes`.

The "early path" covers the 7 topics consumed from two upstream services before any processing-service events arrive:

**From document-intake-service (via CDC → Kafka):**
- `trace.document.received` — fired on RECEIVED, VALIDATED, FORWARDED statuses

**From orchestrator-service (via CDC → Kafka):**
- `saga.lifecycle.started`
- `saga.lifecycle.step-completed`
- `saga.lifecycle.completed`
- `saga.lifecycle.failed`

**No Debezium required for these tests.** Since notification-service is a pure consumer, tests publish events directly to Kafka via `KafkaTemplate`, bypassing the CDC pipeline. CDC correctness is already tested in the upstream services' own integration tests.

---

## Infrastructure Requirements

Same as existing `KafkaConsumerIntegrationTest`:
- PostgreSQL on `localhost:5433` (database: `notification_db`)
- Kafka on `localhost:9093`

Start with:
```bash
./scripts/test-containers-start.sh
```

---

## Design

### New File

```
src/test/java/com/wpanther/notification/integration/EarlyPathIntegrationTest.java
```

Extends `AbstractKafkaConsumerTest` — inherits:
- `@SpringBootTest` wired to `localhost:5433` / `localhost:9093`
- `@ActiveProfiles("consumer-test")`
- `@EnabledIfSystemProperty(named = "integration.tests.enabled", matches = "true")`
- Mocked `EmailNotificationSenderAdapter` and `WebhookNotificationSenderAdapter`
- `testJdbcTemplate` for direct DB queries
- `testKafkaTemplate` for publishing test events
- `sendEvent()`, `awaitNotificationByDocumentId()`, `awaitNotificationByCorrelationId()`, `assertNoNotificationCreatedAfterWait()` helpers
- `@BeforeEach` DB cleanup (`DELETE FROM notifications`, `DELETE FROM outbox_events`)

### Structure

Two `@Nested` groups:

```
EarlyPathIntegrationTest
├── DocumentIntakeEvents (3 tests)
│   ├── shouldPersistIntakeStatOnReceived
│   ├── shouldPersistIntakeStatOnValidated
│   └── shouldPersistIntakeStatOnForwarded
└── OrchestratorLifecycleEvents (4 tests)
    ├── shouldLogSagaStarted              (log only — no notification)
    ├── shouldLogSagaStepCompleted        (log only — no notification)
    ├── shouldCreateNotificationOnSagaCompleted
    └── shouldCreateUrgentNotificationOnSagaFailed
```

---

## Test Specifications

### DocumentIntakeEvents

**Setup needed:** `@BeforeEach` must also clean `document_intake_stats` table.

#### `shouldPersistIntakeStatOnReceived`
- Publish `DocumentReceivedTraceEvent` to `trace.document.received` with `status=RECEIVED`
- Await row in `document_intake_stats` where `document_id = ?`
- Assert: `document_type`, `document_number`, `status = RECEIVED`, `occurred_at` not null

#### `shouldPersistIntakeStatOnValidated`
- Publish `DocumentReceivedTraceEvent` to `trace.document.received` with `status=VALIDATED`
- Await row in `document_intake_stats`
- Assert: `status = VALIDATED`

#### `shouldPersistIntakeStatOnForwarded`
- Publish `DocumentReceivedTraceEvent` to `trace.document.received` with `status=FORWARDED`
- Await row in `document_intake_stats`
- Assert: `status = FORWARDED`

### OrchestratorLifecycleEvents

#### `shouldLogSagaStarted`
- Publish `SagaStartedEvent` to `saga.lifecycle.started`
- Wait 15 seconds
- Assert: `notifications` table count = 0 (log only, no email created)

#### `shouldLogSagaStepCompleted`
- Publish `SagaStepCompletedEvent` to `saga.lifecycle.step-completed`
- Wait 15 seconds
- Assert: `notifications` table count = 0 (log only, no email created)

#### `shouldCreateNotificationOnSagaCompleted`
- Publish `SagaCompletedEvent` to `saga.lifecycle.completed`
- Await notification by `documentId`
- Assert: `type = SAGA_COMPLETED`, `channel = EMAIL`, `status = SENT`, `template_name = saga-completed`
- Assert template variables contain: `sagaId`, `documentId`, `documentNumber`, `documentType`, `stepsExecuted`, `durationMs`

#### `shouldCreateUrgentNotificationOnSagaFailed`
- Publish `SagaFailedEvent` to `saga.lifecycle.failed`
- Await notification by `documentId`
- Assert: `type = SAGA_FAILED`, `channel = EMAIL`, `status = SENT`, `template_name = saga-failed`
- Assert subject contains `URGENT`
- Assert template variables contain: `sagaId`, `documentId`, `failedStep`, `errorMessage`, `retryCount`, `compensationInitiated`

---

## Assertion Strategy

| Route type | Assertion method |
|-----------|-----------------|
| Persists to `document_intake_stats` | `testJdbcTemplate.queryForList()` + Awaitility |
| Log only (no notification) | `assertNoNotificationCreatedAfterWait()` (existing helper, 15s wait) |
| Creates email notification | `awaitNotificationByDocumentId()` or `awaitNotificationByCorrelationId()` |

---

## Required Configuration Change

`src/test/resources/application-consumer-test.yml` is missing `trace-document-received`. Add:
```yaml
kafka:
  topics:
    trace-document-received: trace.document.received   # ← add this
```

## No New Infrastructure

- No new config classes
- No new test configuration classes
- `document_intake_stats` cleanup added to `@BeforeEach` in the new test class only (not added to `AbstractKafkaConsumerTest` to avoid breaking existing tests)

---

## How to Run

```bash
# Start containers
cd /home/wpanther/projects/etax/invoice-microservices
./scripts/test-containers-start.sh

# Run only the new tests
cd services/notification-service
mvn test -Pintegration -Dtest=EarlyPathIntegrationTest

# Run all integration tests
mvn test -Pintegration -Dtest=KafkaConsumerIntegrationTest,EarlyPathIntegrationTest
```
