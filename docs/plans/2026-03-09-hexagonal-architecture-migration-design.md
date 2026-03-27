# Hexagonal Architecture Migration Design
**Service**: notification-service
**Date**: 2026-03-09
**Approach**: Full Canonical Hexagonal Architecture — align with invoice/taxinvoice/ebms services

---

## Context

The 2026-03-03 migration already completed Phase 1 (port interfaces) and Phase 2 (adapter/in|out packages). This document covers the remaining alignment work to reach the same canonical layout used across all other services in the pipeline.

**Current state** — already correct:
- `adapter/in/kafka/`, `adapter/in/rest/`, `adapter/out/persistence/`, `adapter/out/notification/`
- `application/port/in/` (6 use-case interfaces), `application/port/out/` (2 outbound ports)
- `application/service/` (3 services), `application/dto/event/` (Kafka event DTOs — already application-layer objects)
- `domain/model/`, `domain/exception/`, `infrastructure/config/`

**Remaining gaps vs. canonical layout:**
1. `adapter/` is at root level — not under `infrastructure/`
2. `application/port/in/` → should be `application/usecase/`
3. `NotificationRepositoryPort` in `application/port/out/` → should be `domain/repository/NotificationRepository` (domain-owned port)
4. `KafkaTopicsConfig` in `adapter/in/kafka/` → should be in `infrastructure/config/` (bean factory, not adapter)
5. `@Scheduled` methods in `NotificationService` → should be in a scheduler inbound adapter

---

## Goals

- Enforce the dependency rule end-to-end: `domain` ← `application` ← `infrastructure`
- Move `adapter/` under `infrastructure/adapter/` for consistency with all other services
- Rename `application/port/in/` → `application/usecase/`
- Move `NotificationRepositoryPort` → `domain/repository/NotificationRepository` (domain-owned outbound port)
- Move `KafkaTopicsConfig` → `infrastructure/config/`
- Extract `@Scheduled` methods → `infrastructure/adapter/in/scheduler/NotificationSchedulerAdapter`

---

## Layer Responsibilities

| Layer | Purpose | Contents |
|-------|---------|----------|
| `domain/` | Core business rules & models — zero framework imports | `model/`, `exception/`, `repository/` (domain-owned outbound port) |
| `application/` | Orchestration & use cases — imports domain only | `usecase/` (inbound ports), `port/out/` (non-domain outbound ports), `service/`, `dto/` |
| `infrastructure/` | All outside-world interactions | `config/`, `adapter/in/`, `adapter/out/` |

---

## Target Package Structure

```
com.wpanther.notification/
│
├── domain/
│   ├── model/
│   │   ├── Notification.java          (UNCHANGED)
│   │   ├── NotificationStatus.java    (UNCHANGED)
│   │   ├── NotificationChannel.java   (UNCHANGED)
│   │   └── NotificationType.java      (UNCHANGED)
│   ├── exception/
│   │   └── NotificationException.java (UNCHANGED — already here)
│   └── repository/
│       └── NotificationRepository.java (MOVED+RENAMED from application/port/out/NotificationRepositoryPort)
│
├── application/
│   ├── usecase/                        (RENAMED from application/port/in/)
│   │   ├── SendNotificationUseCase.java
│   │   ├── RetryNotificationUseCase.java
│   │   ├── QueryNotificationUseCase.java
│   │   ├── ProcessingEventUseCase.java
│   │   ├── DocumentReceivedEventUseCase.java
│   │   └── SagaEventUseCase.java
│   ├── port/out/
│   │   └── NotificationSenderPort.java  (UNCHANGED — infrastructure concern, not domain-owned)
│   ├── service/
│   │   ├── NotificationService.java     (@Scheduled methods extracted; implements all 6 use cases)
│   │   ├── NotificationDispatcherService.java (UNCHANGED)
│   │   └── NotificationSendingService.java    (UNCHANGED)
│   └── dto/
│       ├── ErrorResponse.java           (UNCHANGED)
│       └── event/                       (UNCHANGED — already application-layer objects, no move needed)
│           ├── InvoiceProcessedEvent.java
│           ├── TaxInvoiceProcessedEvent.java
│           ├── InvoicePdfGeneratedEvent.java
│           ├── PdfSignedEvent.java
│           ├── XmlSignedEvent.java
│           ├── EbmsSentEvent.java
│           ├── DocumentReceivedEvent.java
│           ├── DocumentReceivedCountingEvent.java
│           └── saga/
│               ├── SagaStartedEvent.java
│               ├── SagaStepCompletedEvent.java
│               ├── SagaCompletedEvent.java
│               └── SagaFailedEvent.java
│
└── infrastructure/
    ├── config/
    │   ├── AsyncConfig.java             (UNCHANGED)
    │   ├── GlobalExceptionHandler.java  (UNCHANGED)
    │   ├── OpenTelemetryConfig.java     (UNCHANGED)
    │   ├── WebClientConfig.java         (UNCHANGED)
    │   └── KafkaTopicsConfig.java       (MOVED from adapter/in/kafka/)
    └── adapter/
        ├── in/
        │   ├── kafka/
        │   │   └── NotificationEventRoutes.java        (MOVED from adapter/in/kafka/)
        │   ├── rest/
        │   │   └── NotificationController.java          (MOVED from adapter/in/rest/)
        │   └── scheduler/
        │       └── NotificationSchedulerAdapter.java   (NEW — @Scheduled methods from NotificationService)
        └── out/
            ├── persistence/
            │   ├── NotificationEntity.java              (MOVED from adapter/out/persistence/)
            │   ├── JpaNotificationRepository.java       (MOVED)
            │   ├── NotificationRepositoryAdapter.java   (MOVED; implements domain/repository/NotificationRepository)
            │   ├── JsonMapConverter.java                (MOVED)
            │   └── outbox/
            │       ├── OutboxEventEntity.java           (MOVED)
            │       ├── SpringDataOutboxRepository.java  (MOVED)
            │       └── JpaOutboxEventRepository.java    (MOVED)
            └── notification/
                ├── EmailNotificationSenderAdapter.java  (MOVED from adapter/out/notification/)
                ├── WebhookNotificationSenderAdapter.java (MOVED)
                └── TemplateEngine.java                  (MOVED)
```

---

## Dependency Rule — Import Graph

```
infrastructure/adapter/in/kafka      → application/usecase, application/dto/event
infrastructure/adapter/in/rest       → application/usecase
infrastructure/adapter/in/scheduler  → application/usecase
infrastructure/adapter/out/persistence → domain/repository, domain/model
infrastructure/adapter/out/notification → application/port/out, domain/model, domain/exception

application/service  → application/usecase (implements)
                     → application/port/out (injected)
                     → domain/repository    (injected)
                     → domain/model
                     → application/dto/event

domain/*  → (nothing outside domain)
```

No upward arrows. No `infrastructure` or `application` import inside `domain`.

**Note on `application/dto/event/`:** Event DTOs already live in the application layer — they are plain data objects with only Jackson annotations (no Spring/Kafka imports). Both `application/service/` and `infrastructure/adapter/in/kafka/` import them. This is correct: the Kafka adapter deserialises wire JSON into application-layer objects before calling the use-case interface.

---

## New Component

### `infrastructure/adapter/in/scheduler/NotificationSchedulerAdapter.java`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSchedulerAdapter {

    private final RetryNotificationUseCase retryUseCase;
    private final QueryNotificationUseCase queryUseCase;
    private final SendNotificationUseCase sendUseCase;

    @Scheduled(fixedDelayString = "${app.notification.retry-interval-ms:300000}")
    public void processRetryQueue() {
        log.debug("Running retry queue processing");
        queryUseCase.findByStatus(NotificationStatus.FAILED)
            .forEach(n -> retryUseCase.retryNotification(n.getId()));
    }

    @Scheduled(fixedDelayString = "${app.notification.pending-interval-ms:60000}")
    public void processPendingQueue() {
        log.debug("Running pending queue processing");
        // delegates to queryUseCase.findPendingNotifications() + sendUseCase
    }
}
```

Injects use-case interfaces only — never `NotificationService` directly.

---

## Data Flow

### Kafka Event Flow

```
Kafka → NotificationEventRoutes (Camel RouteBuilder)
    unmarshal JSON → application/dto/event/* type
    ProcessingEventUseCase.handlePdfSigned(event)      [NotificationService]
        Notification.createFromTemplate(type, channel, recipient, ...)
        NotificationRepository.save()
        NotificationSendingService.send(notification)
            [TX1] mark SENDING
            [async] NotificationDispatcherService.dispatch()
                NotificationSenderPort.supports(channel) → select adapter
                EmailNotificationSenderAdapter OR WebhookNotificationSenderAdapter
            [TX2] mark SENT or FAILED
```

### REST Flow

```
POST /api/v1/notifications → NotificationController
    SendNotificationUseCase.createAndSend(...)         [NotificationService]
        Notification.create(...)
        NotificationRepository.save()
        NotificationSendingService.send()

GET  /api/v1/notifications/{id} → NotificationController
    QueryNotificationUseCase.findById(id)              [NotificationService]
        NotificationRepository.findById(id)

POST /api/v1/notifications/{id}/retry → NotificationController
    RetryNotificationUseCase.retryNotification(id)     [NotificationService]
        NotificationRepository.findById(id)
        Notification.markRetrying()
        NotificationSendingService.send()
```

### Scheduled Flow (NEW adapter)

```
@Scheduled (every 5 min) → NotificationSchedulerAdapter.processRetryQueue()
    QueryNotificationUseCase.findByStatus(FAILED)
    RetryNotificationUseCase.retryNotification(id)     (per notification)

@Scheduled (every 1 min) → NotificationSchedulerAdapter.processPendingQueue()
    QueryNotificationUseCase.findPendingNotifications()
    SendNotificationUseCase.sendNotification(...)      (per notification)
```

### Error Handling

| Failure point | Behaviour |
|---|---|
| Kafka deserialization error | Dead Letter Channel → DLQ after Camel retries |
| `EmailNotificationSenderAdapter` throws | TX2 → mark FAILED, increment retryCount |
| `WebhookNotificationSenderAdapter` throws | Same — FAILED + retryCount |
| Max retries exceeded | Scheduler stops retrying (retryCount gate in `NotificationService`) |
| `NotificationRepository` throws | Propagates to Camel route → retry/DLQ |
| REST validation error | `GlobalExceptionHandler` → 400 `ErrorResponse` |

---

## Testing Strategy

### Test Package Structure

```
test/java/com/wpanther/notification/
├── domain/
│   └── model/NotificationTest.java                              (update imports)
├── application/
│   └── service/
│       ├── NotificationServiceTest.java                         (remove @Scheduled tests; update imports)
│       ├── NotificationDispatcherServiceTest.java               (update imports)
│       └── NotificationSendingServiceTest.java                  (update imports)
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   ├── kafka/NotificationEventRoutesTest.java           (MOVED)
    │   │   ├── rest/NotificationControllerTest.java             (MOVED)
    │   │   └── scheduler/NotificationSchedulerAdapterTest.java  (NEW)
    │   └── out/
    │       ├── persistence/
    │       │   ├── NotificationRepositoryAdapterTest.java       (MOVED; update implements ref)
    │       │   ├── NotificationRepositoryIntegrationTest.java   (MOVED)
    │       │   └── outbox/OutboxEventEntityTest.java            (MOVED)
    │       └── notification/
    │           ├── EmailNotificationSenderAdapterTest.java      (MOVED)
    │           └── WebhookNotificationSenderAdapterTest.java    (MOVED)
    └── ApplicationContextLoadTest.java
```

### Coverage Gates

| Scope | Target |
|-------|--------|
| `domain/` | 95%+ line coverage |
| `application/` | 95%+ line coverage |
| `infrastructure/adapter/` | 90%+ line coverage (JaCoCo enforced via `mvn verify`) |

### Key New Tests

- **`NotificationSchedulerAdapterTest`** — verifies delegation to use-case interfaces; verifies no direct reference to `NotificationService` concrete class
- **`NotificationServiceTest`** (updated) — remove `@Scheduled` method tests; verify `NotificationRepository` (new name) injected

---

## Migration Checklist

### Phase 1 — Domain Repository Port
- [ ] Create `domain/repository/NotificationRepository.java` (rename from `NotificationRepositoryPort`)
- [ ] Update `NotificationRepositoryAdapter` to `implements NotificationRepository`
- [ ] Update `NotificationService` import: `NotificationRepositoryPort` → `NotificationRepository`
- [ ] Update `NotificationSendingService` import if needed
- [ ] Delete `application/port/out/NotificationRepositoryPort.java`

### Phase 2 — Rename Inbound Ports to `usecase/`
- [ ] Create `application/usecase/` package; move all 6 interfaces (package declaration change only)
- [ ] Update imports in `NotificationService`, `NotificationController`, `NotificationEventRoutes`
- [ ] Delete `application/port/in/` package

### Phase 3 — Extract Scheduler Adapter
- [ ] Create `infrastructure/adapter/in/scheduler/NotificationSchedulerAdapter.java`
- [ ] Remove `@Scheduled` methods from `NotificationService`
- [ ] Verify `NotificationService` no longer has `@EnableScheduling` or `@Scheduled` annotations

### Phase 4 — Move `adapter/` → `infrastructure/adapter/`
- [ ] Move `adapter/in/kafka/NotificationEventRoutes.java` → `infrastructure/adapter/in/kafka/`
- [ ] Move `adapter/in/kafka/KafkaTopicsConfig.java` → `infrastructure/config/`
- [ ] Move `adapter/in/rest/NotificationController.java` → `infrastructure/adapter/in/rest/`
- [ ] Move all `adapter/out/persistence/` → `infrastructure/adapter/out/persistence/`
- [ ] Move all `adapter/out/notification/` → `infrastructure/adapter/out/notification/`
- [ ] Delete old `adapter/` package tree

### Phase 5 — Test Migration
- [ ] Mirror all package moves in `src/test/java/`
- [ ] Add `NotificationSchedulerAdapterTest`
- [ ] Update `NotificationServiceTest` (remove scheduler tests, update imports)
- [ ] Run `mvn verify` — confirm 90% JaCoCo gate passes

---

## Files Changed Summary

| Action | Count |
|--------|-------|
| New classes | 1 (`NotificationSchedulerAdapter`) + 1 test (`NotificationSchedulerAdapterTest`) |
| Renamed | 1 (`NotificationRepositoryPort` → `NotificationRepository`) |
| Moved only (package change) | ~20 (all adapter, persistence, notification classes) |
| Import updates only | ~8 (services, controller, routes, test classes) |
| Logic changes | 1 (`NotificationService` — remove `@Scheduled` methods) |
| Deleted packages | `application/port/in/`, root-level `adapter/` |
