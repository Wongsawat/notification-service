# Design: Document Intake → Notification Service Integration

**Date:** 2026-03-31
**Scope:** notification-service only (no changes to document-intake-service or orchestrator-service)

---

## Problem

`document-intake-service` publishes `DocumentReceivedTraceEvent` to Kafka topic `trace.document.received` for every lifecycle status change of an incoming document (RECEIVED, VALIDATED, FORWARDED, INVALID). `notification-service` had a dead Route 7 consuming from `document.received` with the wrong event type (`DocumentReceivedCountingEvent`) and no publisher on that topic. Document intake statistics never reached the notification service.

---

## Goal

- Wire `notification-service` to consume `trace.document.received`
- Persist every intake status transition to a dedicated `document_intake_stats` table
- Expose intake counts via a new REST endpoint
- Remove dead code (Route 7, `DocumentReceivedCountingEvent`)

---

## Architecture & Data Flow

```
document-intake-service
  outbox_events table
       │
  Debezium CDC
       │
  Kafka: trace.document.received  (DocumentReceivedTraceEvent, statuses: RECEIVED / VALIDATED / FORWARDED / INVALID)
       │
notification-service
  Route 19 — notification-trace-document-received (new Camel route)
    ↓ deserialize DocumentReceivedTraceEvent
  DocumentIntakeStatUseCase.handleIntakeStat()
    ↓ persist
  document_intake_stats table
    ↓ queryable via
  GET /notifications/statistics/intake
  GET /notifications/statistics/intake?documentId={id}
```

No changes are required in `document-intake-service` — it already publishes the correct event to the correct topic.

---

## Domain Model

New class `DocumentIntakeStat` in `domain/model/`:

| Field | Type | Notes |
|-------|------|-------|
| `id` | `UUID` | generated |
| `documentId` | `String` | maps from `DocumentReceivedTraceEvent.documentId` |
| `documentType` | `String` | e.g. TAX_INVOICE, INVOICE |
| `documentNumber` | `String` | |
| `status` | `String` | RECEIVED, VALIDATED, FORWARDED, INVALID |
| `source` | `String` | API, KAFKA, etc. |
| `correlationId` | `String` | |
| `occurredAt` | `Instant` | maps from `TraceEvent.occurredAt` |

---

## Database Schema

**File:** `src/main/resources/db/migration/V5__create_document_intake_stats_table.sql`

```sql
CREATE TABLE IF NOT EXISTS document_intake_stats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     VARCHAR(255) NOT NULL,
    document_type   VARCHAR(100),
    document_number VARCHAR(255),
    status          VARCHAR(50)  NOT NULL,
    source          VARCHAR(100),
    correlation_id  VARCHAR(255),
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_intake_stats_document_id    ON document_intake_stats (document_id);
CREATE INDEX IF NOT EXISTS idx_intake_stats_status         ON document_intake_stats (status);
CREATE INDEX IF NOT EXISTS idx_intake_stats_document_type  ON document_intake_stats (document_type);
CREATE INDEX IF NOT EXISTS idx_intake_stats_occurred_at    ON document_intake_stats (occurred_at);
```

Single idempotent script — safe for fresh environments and re-runs.

---

## Application Layer

### New use case interface
`DocumentIntakeStatUseCase` in `application/usecase/`:
```java
void handleIntakeStat(DocumentReceivedTraceEvent event);
```

### NotificationService changes
- Implements `DocumentIntakeStatUseCase`
- `handleIntakeStat`: maps `DocumentReceivedTraceEvent` → `DocumentIntakeStat`, calls `documentIntakeStatRepository.save(stat)`, logs at INFO

### Kafka Route (Route 19)
Added to `NotificationEventRoutes.configure()`:
```
from("kafka:" + topics.traceDocumentReceived() + kafkaOptions)
    .routeId("notification-trace-document-received")
    .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedTraceEvent.class)
    .process(this::handleIntakeStat)
```

### KafkaTopicsConfig
New field added to the record:
```java
String traceDocumentReceived
```

### application.yml
New entry under `kafka.topics`:
```yaml
trace-document-received: trace.document.received
```

---

## REST Endpoint

### `GET /notifications/statistics/intake`

Response:
```json
{
  "statusCounts": {
    "RECEIVED": 142,
    "VALIDATED": 138,
    "FORWARDED": 136,
    "INVALID": 4
  },
  "documentTypeCounts": {
    "TAX_INVOICE": 89,
    "INVOICE": 47,
    "ABBREVIATED_TAX_INVOICE": 6
  }
}
```

### `GET /notifications/statistics/intake?documentId={id}`

Response: ordered list of `DocumentIntakeStat` records for that document — shows full lifecycle history for a single document through intake.

New response DTO `DocumentIntakeStatsResponse` in `application/dto/`.

---

## Infrastructure Layer

### JPA entity
`DocumentIntakeStatEntity` in `infrastructure/adapter/out/persistence/` — maps to `document_intake_stats` table.

### Spring Data repository
`SpringDataDocumentIntakeStatRepository extends JpaRepository<DocumentIntakeStatEntity, UUID>` with custom queries:
- `Map<String, Long> countByStatus()` — `GROUP BY status`
- `Map<String, Long> countByDocumentType()` — `GROUP BY document_type`
- `List<DocumentIntakeStatEntity> findByDocumentId(String documentId)` — ordered by `occurred_at ASC`

### Persistence adapter
`JpaDocumentIntakeStatRepository` implements `DocumentIntakeStatRepository` (domain interface).

---

## Removed (Dead Code)

| Item | Location | Reason |
|------|----------|--------|
| Route 7 `notification-document-counting` | `NotificationEventRoutes` | No publisher for `document.received` |
| `DocumentReceivedCountingEvent` | `application/port/in/event/` | Unused input DTO |
| `handleDocumentCounting` method | `DocumentReceivedEventUseCase`, `NotificationService` | Tied to removed event |
| `kafka.topics.document-received` config | `application.yml`, `KafkaTopicsConfig` | Topic replaced by `trace-document-received` |

---

## Testing

### Unit tests (H2)
- `DocumentIntakeStatTest` — domain model construction, all fields mapped correctly
- `JpaDocumentIntakeStatRepositoryTest` — save, countByStatus, countByDocumentType, findByDocumentId
- `NotificationServiceTest` (extend) — `handleIntakeStat` maps event fields and calls repository
- `NotificationControllerTest` (extend) — `GET /notifications/statistics/intake` (aggregate) and with `?documentId=` param

### Integration test
Extend `KafkaConsumerIntegrationTest`:
- Publish `DocumentReceivedTraceEvent` (status=RECEIVED) to `trace.document.received`
- Assert row persisted in `document_intake_stats` with correct `status`, `document_id`, `document_type`

### Deleted tests
- Tests for `DocumentReceivedCountingEvent` and `handleDocumentCounting` — removed with the dead code

---

## Constraints & Notes

- 90% JaCoCo line coverage requirement applies — all new classes must be tested
- `NotificationEventRoutes` uses Apache Camel (not Spring Kafka) — Route 19 follows the same Camel URI pattern as all other routes
- `DocumentReceivedTraceEvent` must be declared as a **local DTO** in `notification/application/port/in/event/` — the notification service does not depend on the intake service's classes. This follows the same pattern as `SagaStartedEvent`, `SagaCompletedEvent`, etc. in `notification/application/port/in/event/saga/`. Use Lombok `@Getter`, extend nothing, declare only the fields needed: `documentId`, `documentType`, `documentNumber`, `status`, `source`, `correlationId`, `occurredAt`
- `@JsonIgnoreProperties(ignoreUnknown = true)` on the local DTO ensures forward compatibility
