# Hexagonal Architecture Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Align notification-service with the canonical Hexagonal Architecture layout used by all other services — move `adapter/` under `infrastructure/`, rename `port/in/` → `usecase/`, promote repository port to `domain/repository/`, and extract `@Scheduled` methods to a dedicated scheduler adapter.

**Architecture:** Five phases executed in order so the codebase compiles and all tests pass after each phase. No business logic changes — this is purely a structural refactor plus the scheduler extraction. The 2026-03-03 migration already completed the hard work; this migration closes the remaining gaps.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.x, JUnit 5, Mockito, JaCoCo 90% gate

---

## Working Directory

All commands run from:
```
/home/wpanther/projects/etax/invoice-microservices/services/notification-service
```

## Verify Starting State

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/notification-service
mvn test -q
```
Expected: `BUILD SUCCESS`. Stop and fix any failures before proceeding.

---

## Phase 1 — Domain Repository Port

### Task 1: Create `domain/repository/NotificationRepository` and update all references

`NotificationRepositoryPort` lives in `application/port/out/` but is a domain-owned contract (defines what the domain needs from persistence). Move it to `domain/repository/` and rename to drop the `Port` suffix, matching the pattern used across all other services.

**Files:**
- Create: `src/main/java/com/wpanther/notification/domain/repository/NotificationRepository.java`
- Modify: `src/main/java/com/wpanther/notification/adapter/out/persistence/NotificationRepositoryAdapter.java`
- Modify: `src/main/java/com/wpanther/notification/application/service/NotificationService.java`
- Modify: `src/main/java/com/wpanther/notification/application/service/NotificationSendingService.java`
- Delete: `src/main/java/com/wpanther/notification/application/port/out/NotificationRepositoryPort.java`

**Step 1: Create `NotificationRepository`**

```java
// src/main/java/com/wpanther/notification/domain/repository/NotificationRepository.java
package com.wpanther.notification.domain.repository;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain-owned outbound port for persisting Notification aggregates.
 * Infrastructure layer provides the implementation via JPA adapter.
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    List<Notification> findByStatus(NotificationStatus status);

    List<Notification> findByInvoiceId(String invoiceId);

    List<Notification> findByInvoiceNumber(String invoiceNumber);

    List<Notification> findByRecipient(String recipient);

    List<Notification> findByType(NotificationType type);

    List<Notification> findStaleSendingNotifications(LocalDateTime threshold, int limit);

    List<Notification> findFailedNotifications(int maxRetries, int limit);

    List<Notification> findPendingNotifications(int limit);

    List<Notification> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatus(NotificationStatus status);

    long countByType(NotificationType type);

    void deleteById(UUID id);
}
```

**Step 2: Update `NotificationRepositoryAdapter`**

Change the `implements` clause and import:
- `implements NotificationRepositoryPort` → `implements NotificationRepository`
- `import com.wpanther.notification.application.port.out.NotificationRepositoryPort;` → `import com.wpanther.notification.domain.repository.NotificationRepository;`

**Step 3: Update `NotificationService`**

Replace:
```java
import com.wpanther.notification.application.port.out.NotificationRepositoryPort;
// ...
private final NotificationRepositoryPort repository;
```
With:
```java
import com.wpanther.notification.domain.repository.NotificationRepository;
// ...
private final NotificationRepository repository;
```

**Step 4: Update `NotificationSendingService`** — same import replacement if it references `NotificationRepositoryPort`.

Run to find all remaining references:
```bash
grep -r "NotificationRepositoryPort" src/main/java --include="*.java" -l
```

**Step 5: Delete old file**

```bash
rm src/main/java/com/wpanther/notification/application/port/out/NotificationRepositoryPort.java
```

**Step 6: Compile and test**

```bash
mvn test -q
```
Expected: `BUILD SUCCESS`

**Step 7: Commit**

```bash
git add -A
git commit -m "Move NotificationRepositoryPort to domain/repository/NotificationRepository"
```

---

## Phase 2 — Rename Inbound Ports to `usecase/`

### Task 2: Rename `application/port/in/` → `application/usecase/`

Six use-case interface files. For each: create at new path with updated package declaration, delete old file, update all import references.

**Files to create (package change only — no content changes):**

| Old path | New path |
|----------|----------|
| `application/port/in/SendNotificationUseCase.java` | `application/usecase/SendNotificationUseCase.java` |
| `application/port/in/RetryNotificationUseCase.java` | `application/usecase/RetryNotificationUseCase.java` |
| `application/port/in/QueryNotificationUseCase.java` | `application/usecase/QueryNotificationUseCase.java` |
| `application/port/in/ProcessingEventUseCase.java` | `application/usecase/ProcessingEventUseCase.java` |
| `application/port/in/DocumentReceivedEventUseCase.java` | `application/usecase/DocumentReceivedEventUseCase.java` |
| `application/port/in/SagaEventUseCase.java` | `application/usecase/SagaEventUseCase.java` |

For each file, change only the first line:
`package com.wpanther.notification.application.port.in;`
→ `package com.wpanther.notification.application.usecase;`

**Step 1: Create all six files at new path** with updated package declaration.

**Step 2: Update imports in all referencing files**

Replace `import com.wpanther.notification.application.port.in.` with `import com.wpanther.notification.application.usecase.` in:
- `src/main/java/com/wpanther/notification/application/service/NotificationService.java`
- `src/main/java/com/wpanther/notification/adapter/in/kafka/NotificationEventRoutes.java`
- `src/main/java/com/wpanther/notification/adapter/in/rest/NotificationController.java`

Run to find any remaining references:
```bash
grep -r "application.port.in" src/main/java --include="*.java" -l
```

**Step 3: Delete old files and package**

```bash
rm src/main/java/com/wpanther/notification/application/port/in/SendNotificationUseCase.java
rm src/main/java/com/wpanther/notification/application/port/in/RetryNotificationUseCase.java
rm src/main/java/com/wpanther/notification/application/port/in/QueryNotificationUseCase.java
rm src/main/java/com/wpanther/notification/application/port/in/ProcessingEventUseCase.java
rm src/main/java/com/wpanther/notification/application/port/in/DocumentReceivedEventUseCase.java
rm src/main/java/com/wpanther/notification/application/port/in/SagaEventUseCase.java
rmdir src/main/java/com/wpanther/notification/application/port/in
rmdir src/main/java/com/wpanther/notification/application/port 2>/dev/null || true
```

**Step 4: Compile and test**

```bash
mvn test -q
```
Expected: `BUILD SUCCESS`

**Step 5: Commit**

```bash
git add -A
git commit -m "Rename application/port/in/ to application/usecase/"
```

---

## Phase 3 — Extract Scheduler Adapter

### Task 3: Create `NotificationSchedulerAdapter` and remove `@Scheduled` from `NotificationService`

`NotificationService` has three `@Scheduled` methods. These are driving-side triggers (like Kafka consumers) and belong in `infrastructure/adapter/in/scheduler/`. After extraction, `NotificationService` has no `@Scheduled` or framework scheduling imports.

**Files:**
- Create: `src/main/java/com/wpanther/notification/infrastructure/adapter/in/scheduler/NotificationSchedulerAdapter.java`
- Modify: `src/main/java/com/wpanther/notification/application/service/NotificationService.java`

**Step 1: Create `NotificationSchedulerAdapter`**

The three scheduled methods are extracted verbatim from `NotificationService`. The adapter injects use-case interfaces only:

```java
// src/main/java/com/wpanther/notification/infrastructure/adapter/in/scheduler/NotificationSchedulerAdapter.java
package com.wpanther.notification.infrastructure.adapter.in.scheduler;

import com.wpanther.notification.application.usecase.QueryNotificationUseCase;
import com.wpanther.notification.application.usecase.RetryNotificationUseCase;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Inbound scheduler adapter: drives scheduled notification maintenance tasks.
 * <p>
 * Injects use-case interfaces only — never the concrete NotificationService.
 * Three tasks:
 *   1. Recover stale SENDING notifications (every 2 min)
 *   2. Retry failed notifications (every 5 min)
 *   3. Process pending notifications (every 1 min)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSchedulerAdapter {

    private final RetryNotificationUseCase retryUseCase;
    private final QueryNotificationUseCase queryUseCase;
    private final NotificationRepository repository;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

    @Value("${app.notification.stale-sending-timeout-ms:300000}")
    private long staleSendingTimeoutMs;

    /**
     * Recovers notifications stuck in SENDING state (e.g. after a process crash).
     * Resets them to FAILED so the normal retry sweeper can pick them up.
     */
    @Scheduled(fixedDelayString = "${app.notification.stale-sending-check-interval:120000}")
    @Transactional
    public void recoverStaleSendingNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(staleSendingTimeoutMs / 1000);
        List<Notification> stale = repository.findStaleSendingNotifications(threshold, 100);

        if (stale.isEmpty()) {
            return;
        }

        log.warn("Found {} stale SENDING notifications to recover", stale.size());

        for (Notification notification : stale) {
            try {
                notification.markFailed("Recovered from stale SENDING state");
                repository.save(notification);
                log.info("Recovered stale notification: id={}", notification.getId());
            } catch (Exception e) {
                log.error("Failed to recover stale notification: id={}", notification.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.notification.retry-interval:300000}")
    public void retryFailedNotifications() {
        List<Notification> failedNotifications = repository.findFailedNotifications(maxRetries, 100);
        log.info("Found {} failed notifications to retry", failedNotifications.size());
        failedNotifications.forEach(n -> {
            try {
                retryUseCase.retryNotification(n.getId());
            } catch (Exception e) {
                log.error("Failed to retry notification: id={}", n.getId(), e);
            }
        });
    }

    @Scheduled(fixedDelayString = "${app.notification.processing-interval:60000}")
    public void processPendingNotifications() {
        List<Notification> pending = repository.findPendingNotifications(100);
        log.debug("Processing {} pending notifications", pending.size());
        pending.forEach(n -> {
            try {
                retryUseCase.retryNotification(n.getId());
            } catch (Exception e) {
                log.error("Failed to process pending notification: id={}", n.getId(), e);
            }
        });
    }
}
```

**Step 2: Remove the three `@Scheduled` methods from `NotificationService`**

Delete these three methods entirely from `NotificationService`:
- `recoverStaleSendingNotifications()` (lines ~423–444)
- `retryFailedNotifications()` (lines ~446–468)
- `processPendingNotifications()` (lines ~469–484)

Also remove the now-unused import:
```java
import org.springframework.scheduling.annotation.Scheduled;
```

Also remove `staleSendingTimeoutMs` field from `NotificationService` if used only by the extracted method.

**Step 3: Compile and test**

```bash
mvn test -q
```
Expected: `BUILD SUCCESS`

**Step 4: Commit**

```bash
git add -A
git commit -m "Extract @Scheduled methods to NotificationSchedulerAdapter"
```

---

## Phase 4 — Move `adapter/` Under `infrastructure/`

### Task 4: Move all `adapter/` packages to `infrastructure/adapter/` and move `KafkaTopicsConfig`

This is the largest phase — ~12 files move, package declarations change. Work sub-package by sub-package to stay compilable.

**Complete file move map:**

| Old path | New path |
|----------|----------|
| `adapter/in/kafka/NotificationEventRoutes.java` | `infrastructure/adapter/in/kafka/NotificationEventRoutes.java` |
| `adapter/in/kafka/KafkaTopicsConfig.java` | `infrastructure/config/KafkaTopicsConfig.java` |
| `adapter/in/rest/NotificationController.java` | `infrastructure/adapter/in/rest/NotificationController.java` |
| `adapter/out/persistence/NotificationEntity.java` | `infrastructure/adapter/out/persistence/NotificationEntity.java` |
| `adapter/out/persistence/JpaNotificationRepository.java` | `infrastructure/adapter/out/persistence/JpaNotificationRepository.java` |
| `adapter/out/persistence/NotificationRepositoryAdapter.java` | `infrastructure/adapter/out/persistence/NotificationRepositoryAdapter.java` |
| `adapter/out/persistence/JsonMapConverter.java` | `infrastructure/adapter/out/persistence/JsonMapConverter.java` |
| `adapter/out/persistence/outbox/OutboxEventEntity.java` | `infrastructure/adapter/out/persistence/outbox/OutboxEventEntity.java` |
| `adapter/out/persistence/outbox/SpringDataOutboxRepository.java` | `infrastructure/adapter/out/persistence/outbox/SpringDataOutboxRepository.java` |
| `adapter/out/persistence/outbox/JpaOutboxEventRepository.java` | `infrastructure/adapter/out/persistence/outbox/JpaOutboxEventRepository.java` |
| `adapter/out/notification/EmailNotificationSenderAdapter.java` | `infrastructure/adapter/out/notification/EmailNotificationSenderAdapter.java` |
| `adapter/out/notification/WebhookNotificationSenderAdapter.java` | `infrastructure/adapter/out/notification/WebhookNotificationSenderAdapter.java` |
| `adapter/out/notification/TemplateEngine.java` | `infrastructure/adapter/out/notification/TemplateEngine.java` |

**Step 1: Move `adapter/in/kafka/NotificationEventRoutes.java`**

Create at new path. Change only package declaration:
`package com.wpanther.notification.adapter.in.kafka;`
→ `package com.wpanther.notification.infrastructure.adapter.in.kafka;`

**Step 2: Move `KafkaTopicsConfig.java` to `infrastructure/config/`**

Create at new path. Change package to `com.wpanther.notification.infrastructure.config`.
Update any `@Import` or `@ComponentScan` references if present.

**Step 3: Move `adapter/in/rest/NotificationController.java`**

Package: `com.wpanther.notification.infrastructure.adapter.in.rest`

**Step 4: Move all `adapter/out/persistence/` files**

For each file, change package to `com.wpanther.notification.infrastructure.adapter.out.persistence`.
For outbox sub-package: `com.wpanther.notification.infrastructure.adapter.out.persistence.outbox`.

**Step 5: Move all `adapter/out/notification/` files**

Package: `com.wpanther.notification.infrastructure.adapter.out.notification`

**Step 6: Update cross-references**

After moving, check for any classes that import from the old `adapter.*` packages:
```bash
grep -r "import com.wpanther.notification.adapter\." src/main/java --include="*.java" -l
```
Each hit needs its import updated to `infrastructure.adapter.*`.

**Step 7: Delete old `adapter/` tree**

```bash
rm -rf src/main/java/com/wpanther/notification/adapter/
```

**Step 8: Compile and test**

```bash
mvn test -q
```
Expected: `BUILD SUCCESS`

**Step 9: Commit**

```bash
git add -A
git commit -m "Move adapter/ under infrastructure/adapter/, move KafkaTopicsConfig to infrastructure/config/"
```

---

## Phase 5 — Test Migration

### Task 5: Mirror all package moves in test sources

Every production class that moved has a corresponding test. Update each test file's package declaration and imports to match the new location.

**Test file move map:**

| Old test path | New test path |
|---------------|---------------|
| `adapter/in/rest/GlobalExceptionHandlerTest.java` | `infrastructure/config/GlobalExceptionHandlerTest.java` |
| `adapter/in/rest/NotificationControllerTest.java` | `infrastructure/adapter/in/rest/NotificationControllerTest.java` |
| `adapter/out/notification/EmailNotificationSenderAdapterTest.java` | `infrastructure/adapter/out/notification/EmailNotificationSenderAdapterTest.java` |
| `adapter/out/notification/TemplateEngineTest.java` | `infrastructure/adapter/out/notification/TemplateEngineTest.java` |
| `adapter/out/notification/WebhookNotificationSenderAdapterTest.java` | `infrastructure/adapter/out/notification/WebhookNotificationSenderAdapterTest.java` |
| `adapter/out/persistence/JsonMapConverterTest.java` | `infrastructure/adapter/out/persistence/JsonMapConverterTest.java` |
| `adapter/out/persistence/NotificationRepositoryAdapterTest.java` | `infrastructure/adapter/out/persistence/NotificationRepositoryAdapterTest.java` |
| `adapter/out/persistence/outbox/JpaOutboxEventRepositoryTest.java` | `infrastructure/adapter/out/persistence/outbox/JpaOutboxEventRepositoryTest.java` |
| `adapter/out/persistence/outbox/OutboxEventEntityTest.java` | `infrastructure/adapter/out/persistence/outbox/OutboxEventEntityTest.java` |

For each file: create at new path (update `package` declaration + update imports that reference old `adapter.*` or `application.port.in.*` or `application.port.out.NotificationRepositoryPort` packages), then delete the old file.

Also update these application-layer tests:
- `application/service/NotificationServiceTest.java` — update `NotificationRepositoryPort` → `NotificationRepository` import; update `application.port.in.*` → `application.usecase.*` imports; remove any `@Scheduled` method tests (those move to scheduler test)
- `application/service/NotificationDispatcherServiceTest.java` — update port imports if any
- `application/service/NotificationSendingServiceTest.java` — update port imports if any

**Step 1: Update application service tests**

```bash
# Find all test files still referencing old packages
grep -r "application\.port\.in\.\|application\.port\.out\.NotificationRepositoryPort\|notification\.adapter\." \
  src/test/java --include="*.java" -l
```

For each file found: update import lines only.

**Step 2: Move adapter test files**

For each row in the move map above: create file at new path with updated `package` declaration and imports, then delete old file.

**Step 3: Delete old test directories**

```bash
rm -rf src/test/java/com/wpanther/notification/adapter/
```

**Step 4: Compile tests**

```bash
mvn test-compile -q
```
Expected: `BUILD SUCCESS`

**Step 5: Commit**

```bash
git add -A
git commit -m "Mirror package moves in test sources"
```

---

### Task 6: Add `NotificationSchedulerAdapterTest` and run full coverage check

**Files:**
- Create: `src/test/java/com/wpanther/notification/infrastructure/adapter/in/scheduler/NotificationSchedulerAdapterTest.java`

**Step 1: Create the test**

```java
// src/test/java/com/wpanther/notification/infrastructure/adapter/in/scheduler/NotificationSchedulerAdapterTest.java
package com.wpanther.notification.infrastructure.adapter.in.scheduler;

import com.wpanther.notification.application.usecase.QueryNotificationUseCase;
import com.wpanther.notification.application.usecase.RetryNotificationUseCase;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerAdapterTest {

    @Mock private RetryNotificationUseCase retryUseCase;
    @Mock private QueryNotificationUseCase queryUseCase;
    @Mock private NotificationRepository repository;

    @InjectMocks private NotificationSchedulerAdapter scheduler;

    @Test
    void retryFailedNotifications_delegatesToRetryUseCaseForEachFailed() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Notification n1 = mock(Notification.class);
        Notification n2 = mock(Notification.class);
        when(n1.getId()).thenReturn(id1);
        when(n2.getId()).thenReturn(id2);
        when(repository.findFailedNotifications(anyInt(), anyInt())).thenReturn(List.of(n1, n2));

        scheduler.retryFailedNotifications();

        verify(retryUseCase).retryNotification(id1);
        verify(retryUseCase).retryNotification(id2);
    }

    @Test
    void retryFailedNotifications_continuesOnException() {
        Notification n = mock(Notification.class);
        when(n.getId()).thenReturn(UUID.randomUUID());
        when(repository.findFailedNotifications(anyInt(), anyInt())).thenReturn(List.of(n));
        doThrow(new RuntimeException("send failed")).when(retryUseCase).retryNotification(any());

        // Should not throw
        scheduler.retryFailedNotifications();
    }

    @Test
    void processPendingNotifications_delegatesToRetryUseCaseForEachPending() {
        UUID id = UUID.randomUUID();
        Notification n = mock(Notification.class);
        when(n.getId()).thenReturn(id);
        when(repository.findPendingNotifications(anyInt())).thenReturn(List.of(n));

        scheduler.processPendingNotifications();

        verify(retryUseCase).retryNotification(id);
    }

    @Test
    void recoverStaleSendingNotifications_marksStaleFailed() {
        Notification n = mock(Notification.class);
        when(repository.findStaleSendingNotifications(any(LocalDateTime.class), anyInt()))
            .thenReturn(List.of(n));

        scheduler.recoverStaleSendingNotifications();

        verify(n).markFailed("Recovered from stale SENDING state");
        verify(repository).save(n);
    }

    @Test
    void recoverStaleSendingNotifications_noopWhenNoneStale() {
        when(repository.findStaleSendingNotifications(any(), anyInt())).thenReturn(List.of());

        scheduler.recoverStaleSendingNotifications();

        verifyNoInteractions(retryUseCase);
    }

    @Test
    void allThreeMethodsHaveScheduledAnnotation() {
        String[] scheduledMethods = {"retryFailedNotifications", "processPendingNotifications",
                                      "recoverStaleSendingNotifications"};
        for (String methodName : scheduledMethods) {
            Method method = Arrays.stream(NotificationSchedulerAdapter.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Method not found: " + methodName));
            assertThat(method.isAnnotationPresent(Scheduled.class))
                .as("Method %s should be @Scheduled", methodName)
                .isTrue();
        }
    }

    @Test
    void injectsRetryUseCaseInterface_notConcreteClass() throws Exception {
        var field = NotificationSchedulerAdapter.class.getDeclaredField("retryUseCase");
        assertThat(field.getType()).isEqualTo(RetryNotificationUseCase.class);
    }

    @Test
    void injectsQueryUseCaseInterface_notConcreteClass() throws Exception {
        var field = NotificationSchedulerAdapter.class.getDeclaredField("queryUseCase");
        assertThat(field.getType()).isEqualTo(QueryNotificationUseCase.class);
    }
}
```

**Step 2: Run tests**

```bash
mvn test -q
```
Expected: `BUILD SUCCESS`

**Step 3: Run full coverage verification**

```bash
mvn verify -q
```
Expected: `BUILD SUCCESS` with JaCoCo 90% gate passing.

If coverage gate fails, identify gaps:
```bash
mvn verify
# Check target/site/jacoco/index.html
```

**Step 4: Commit**

```bash
git add -A
git commit -m "Add NotificationSchedulerAdapterTest, run coverage verification"
```

---

## Final Verification

**Step 1: Confirm no old package references remain**

```bash
grep -r "import com.wpanther.notification.adapter\." src/ --include="*.java"
grep -r "import com.wpanther.notification.application.port.in\." src/ --include="*.java"
grep -r "import com.wpanther.notification.application.port.out.NotificationRepositoryPort" src/ --include="*.java"
```
Expected: no output for any command.

**Step 2: Confirm dependency rule (domain imports nothing from application/infrastructure)**

```bash
grep -r "import com.wpanther.notification.application\." \
  src/main/java/com/wpanther/notification/domain/ --include="*.java"
grep -r "import com.wpanther.notification.infrastructure\." \
  src/main/java/com/wpanther/notification/domain/ --include="*.java"
```
Expected: no output.

**Step 3: Full clean build**

```bash
mvn clean verify
```
Expected: `BUILD SUCCESS`

---

## Files Changed Summary

| Action | Count |
|--------|-------|
| New classes | 1 (`NotificationSchedulerAdapter`) + 1 test |
| Renamed | 1 (`NotificationRepositoryPort` → `NotificationRepository`) |
| Moved only (package change) | ~13 production files, ~9 test files |
| Import updates | ~8 (services, controller, routes, test files) |
| Logic changes | 1 (`NotificationService` — remove 3 `@Scheduled` methods) |
| Deleted packages | `application/port/in/`, root-level `adapter/` |
