# Hexagonal Architecture Migration Design

**Date:** 2026-03-03
**Service:** notification-service (port 8085)
**Status:** Approved

---

## Overview

Migrate the notification service from its current DDD-layered structure (`domain/`, `application/`, `infrastructure/`) to a canonical Hexagonal Architecture (Ports & Adapters) with explicit `application/port/in|out/` and `adapter/in|out/` packages.

The existing logic, 90% JaCoCo coverage requirement, and all 147 unit tests are preserved throughout. No schema changes, no new dependencies.

---

## Final Package Structure

```
src/main/java/com/wpanther/notification/
├── domain/
│   ├── model/                          ← Notification, enums (unchanged)
│   │   ├── Notification.java
│   │   ├── NotificationStatus.java
│   │   ├── NotificationChannel.java
│   │   └── NotificationType.java
│   └── exception/                      ← NEW: NotificationException moved here
│       └── NotificationException.java
│
├── application/
│   ├── port/
│   │   ├── in/                         ← NEW: input port interfaces (use case contracts)
│   │   │   ├── SendNotificationUseCase.java
│   │   │   ├── RetryNotificationUseCase.java
│   │   │   ├── QueryNotificationUseCase.java
│   │   │   ├── ProcessingEventUseCase.java
│   │   │   ├── DocumentReceivedEventUseCase.java
│   │   │   └── SagaEventUseCase.java
│   │   └── out/                        ← MOVED from domain/repository + domain/service
│   │       ├── NotificationRepositoryPort.java
│   │       └── NotificationSenderPort.java
│   ├── service/
│   │   ├── NotificationService.java    ← implements all 6 input port interfaces
│   │   ├── NotificationDispatcherService.java
│   │   └── NotificationSendingService.java
│   └── dto/
│       └── ErrorResponse.java          ← unchanged
│
├── adapter/
│   ├── in/
│   │   ├── rest/                       ← MOVED from application/controller
│   │   │   └── NotificationController.java  ← injects port interfaces, not concrete classes
│   │   └── kafka/                      ← MOVED from infrastructure/messaging
│   │       ├── NotificationEventRoutes.java ← thin: unmarshal + delegate to port interfaces
│   │       ├── InvoiceProcessedEvent.java
│   │       ├── TaxInvoiceProcessedEvent.java
│   │       ├── PdfGeneratedEvent.java
│   │       ├── PdfSignedEvent.java
│   │       ├── XmlSignedEvent.java
│   │       ├── EbmsSentEvent.java
│   │       ├── DocumentReceivedEvent.java
│   │       ├── DocumentReceivedCountingEvent.java
│   │       └── saga/
│   │           ├── SagaStartedEvent.java
│   │           ├── SagaStepCompletedEvent.java
│   │           ├── SagaCompletedEvent.java
│   │           └── SagaFailedEvent.java
│   └── out/
│       ├── persistence/                ← MOVED from infrastructure/persistence
│       │   ├── NotificationEntity.java
│       │   ├── JpaNotificationRepository.java
│       │   ├── NotificationRepositoryAdapter.java  ← renamed from NotificationRepositoryImpl
│       │   ├── JsonMapConverter.java
│       │   └── outbox/
│       │       ├── OutboxEventEntity.java
│       │       ├── SpringDataOutboxRepository.java
│       │       └── JpaOutboxEventRepository.java
│       └── notification/               ← MOVED from infrastructure/notification
│           ├── EmailNotificationSenderAdapter.java    ← renamed from EmailNotificationSender
│           ├── WebhookNotificationSenderAdapter.java  ← renamed from WebhookNotificationSender
│           └── TemplateEngine.java
│
└── infrastructure/
    └── config/                         ← unchanged (wires Spring context, not adapter-specific)
        ├── AsyncConfig.java
        ├── WebClientConfig.java
        ├── KafkaTopicsConfig.java
        ├── GlobalExceptionHandler.java
        └── OpenTelemetryConfig.java
```

---

## Input Ports (Use Case Interfaces)

### application/port/in/SendNotificationUseCase.java
```java
public interface SendNotificationUseCase {
    UUID sendNotification(SendNotificationCommand command);
    UUID createAndSend(NotificationType type, NotificationChannel channel,
                       String recipient, String subject, String body,
                       String invoiceId, String invoiceNumber, String correlationId);
}
```

### application/port/in/RetryNotificationUseCase.java
```java
public interface RetryNotificationUseCase {
    void retryNotification(UUID id);
}
```

### application/port/in/QueryNotificationUseCase.java
```java
public interface QueryNotificationUseCase {
    Notification findById(UUID id);
    List<Notification> findByInvoiceId(String invoiceId);
    List<Notification> findByStatus(NotificationStatus status);
    Map<String, Long> getStatistics();
}
```

### application/port/in/ProcessingEventUseCase.java
```java
public interface ProcessingEventUseCase {
    void handleInvoiceProcessed(InvoiceProcessedEvent event);
    void handleTaxInvoiceProcessed(TaxInvoiceProcessedEvent event);
    void handlePdfGenerated(PdfGeneratedEvent event);
    void handlePdfSigned(PdfSignedEvent event);
    void handleXmlSigned(XmlSignedEvent event);
    void handleEbmsSent(EbmsSentEvent event);
}
```

### application/port/in/DocumentReceivedEventUseCase.java
```java
public interface DocumentReceivedEventUseCase {
    void handleDocumentReceived(DocumentReceivedEvent event);
    void handleDocumentCounting(DocumentReceivedCountingEvent event);
}
```

### application/port/in/SagaEventUseCase.java
```java
public interface SagaEventUseCase {
    void handleSagaStarted(SagaStartedEvent event);
    void handleSagaStepCompleted(SagaStepCompletedEvent event);
    void handleSagaCompleted(SagaCompletedEvent event);
    void handleSagaFailed(SagaFailedEvent event);
}
```

---

## Output Ports

### application/port/out/NotificationRepositoryPort.java
Renamed from `domain/repository/NotificationRepository`. Same method signatures.

### application/port/out/NotificationSenderPort.java
Renamed from `domain/service/NotificationSender`. Same method signatures.
`NotificationException` extracted to `domain/exception/NotificationException.java`.

---

## Adapter Responsibilities

### adapter/in/rest/NotificationController.java
- Injects `SendNotificationUseCase`, `RetryNotificationUseCase`, `QueryNotificationUseCase`
- No direct reference to `NotificationService` concrete class
- `ErrorResponse.java` DTO moves to `adapter/in/rest/dto/` or stays in `application/dto/`

### adapter/in/kafka/NotificationEventRoutes.java
- Injects `ProcessingEventUseCase`, `DocumentReceivedEventUseCase`, `SagaEventUseCase`
- Route handlers become 2–3 lines: unmarshal JSON → call use case method
- All business logic (template selection, `Notification.createFromTemplate`) moves into `NotificationService`
- Event DTOs remain in `adapter/in/kafka/` (Kafka wire format, not domain concepts)

### adapter/out/persistence/NotificationRepositoryAdapter.java
- Implements `NotificationRepositoryPort`
- Renamed from `NotificationRepositoryImpl`
- Manual builder-based mapping (no MapStruct) preserved

### adapter/out/notification/EmailNotificationSenderAdapter.java
### adapter/out/notification/WebhookNotificationSenderAdapter.java
- Both implement `NotificationSenderPort`
- Internal logic unchanged; only class names and packages change

---

## Dependency Direction

```
adapter/in  ──→  application/port/in  ──→  application/service
                                                    │
                                      application/port/out
                                                    ↑
                              adapter/out ──────────┘

domain ←── application ←── adapter   (domain has NO outward imports)
```

Rules:
- `domain/` imports nothing from application or adapter
- `application/port/` imports only domain model
- `application/service/` imports domain model + both port layers
- `adapter/` imports application ports (never `application/service/` directly)
- Config may import anything it needs to wire beans

---

## Migration Phases

### Phase 1 — Add Port Interfaces (tests stay green throughout)

1. Create `domain/exception/NotificationException.java`
2. Create `application/port/out/NotificationRepositoryPort.java` (copy from `domain/repository/NotificationRepository`)
3. Create `application/port/out/NotificationSenderPort.java` (copy from `domain/service/NotificationSender`, reference new exception)
4. Create all 6 `application/port/in/` interfaces
5. Update `NotificationService` to implement all 6 input port interfaces
6. Update `NotificationRepositoryImpl` to implement `NotificationRepositoryPort`
7. Update `EmailNotificationSender` / `WebhookNotificationSender` to implement `NotificationSenderPort`
8. Move event handler business logic from `NotificationEventRoutes` into `NotificationService`
9. Update `NotificationController` to inject use case port interfaces
10. Update `NotificationEventRoutes` to inject event use case port interfaces
11. Delete `domain/repository/NotificationRepository.java` and `domain/service/NotificationSender.java`
12. Run `mvn test` — all 147 tests must pass
13. Commit: `Add hexagonal port interfaces and thin Camel routes`

### Phase 2 — Rename Packages to adapter/in|out (tests stay green throughout)

1. Move `infrastructure/persistence/` → `adapter/out/persistence/`
2. Rename `NotificationRepositoryImpl` → `NotificationRepositoryAdapter`
3. Move `infrastructure/messaging/` → `adapter/in/kafka/`
4. Move `infrastructure/notification/` → `adapter/out/notification/`
5. Rename `EmailNotificationSender` → `EmailNotificationSenderAdapter`
6. Rename `WebhookNotificationSender` → `WebhookNotificationSenderAdapter`
7. Move `application/controller/` → `adapter/in/rest/`
8. Update all imports in source and test files
9. Run `mvn test` — all 147 tests must pass
10. Commit: `Rename packages to hexagonal adapter/in|out structure`

---

## What Does NOT Change

- Database schema (no Flyway migration needed)
- All business logic in `NotificationService`, `NotificationSendingService`, `NotificationDispatcherService`
- The 3-phase transaction strategy in `NotificationSendingService`
- Camel route configuration (topics, error handling, dead letter channel)
- All 147 unit tests (only import paths change)
- All 7 integration tests
- `application.yml`, `pom.xml`, Dockerfile

---

## Success Criteria

- `mvn test` passes with 147 unit tests after each phase
- JaCoCo 90% line coverage enforced via `mvn verify`
- No class references concrete implementations across adapter ↔ application boundary
- `NotificationController` and `NotificationEventRoutes` only import `application/port/in/` interfaces
- `domain/` package has zero imports from Spring Framework
