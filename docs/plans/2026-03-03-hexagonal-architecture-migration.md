# Hexagonal Architecture Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate the notification service from a DDD-layered structure to canonical Hexagonal Architecture (Ports & Adapters), with explicit `application/port/in|out/` and `adapter/in|out/` packages, enforcing clean dependency direction.

**Architecture:** Two-phase migration — Phase 1 adds port interfaces while keeping package names, Phase 2 renames packages to `adapter/in|out`. Tests (147 unit) stay green after each phase.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, Lombok, JUnit 5, Mockito

---

## Phase 1: Add Port Interfaces

### Task 1: Create domain/exception/NotificationException.java

Extract `NotificationException` from its current home as an inner class of `domain/service/NotificationSender.java` into its own top-level class. This enables the output port interface to reference it cleanly without circular imports.

**Files:**
- Create: `src/main/java/com/wpanther/notification/domain/exception/NotificationException.java`

**Step 1: Create the exception class**

```java
// src/main/java/com/wpanther/notification/domain/exception/NotificationException.java
package com.wpanther.notification.domain.exception;

public class NotificationException extends Exception {

    public NotificationException(String message) {
        super(message);
    }

    public NotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Step 2: Verify it compiles**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/notification-service
mvn compile -q
```
Expected: BUILD SUCCESS with no errors.

---

### Task 2: Create output port — NotificationRepositoryPort

Copy the existing `domain/repository/NotificationRepository.java` interface to the new output port location. Do NOT delete the original yet (that happens in Task 13). Both interfaces exist in parallel during Phase 1.

**Files:**
- Create: `src/main/java/com/wpanther/notification/application/port/out/NotificationRepositoryPort.java`

**Step 1: Create the output port interface**

```java
// src/main/java/com/wpanther/notification/application/port/out/NotificationRepositoryPort.java
package com.wpanther.notification.application.port.out;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port: persistence contract for Notification aggregate.
 * Implemented by {@link com.wpanther.notification.infrastructure.persistence.NotificationRepositoryImpl}.
 */
public interface NotificationRepositoryPort {

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

**Step 2: Verify it compiles**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

---

### Task 3: Create output port — NotificationSenderPort

Create the sender output port using the new `NotificationException` from `domain/exception`.

**Files:**
- Create: `src/main/java/com/wpanther/notification/application/port/out/NotificationSenderPort.java`

**Step 1: Create the output port interface**

```java
// src/main/java/com/wpanther/notification/application/port/out/NotificationSenderPort.java
package com.wpanther.notification.application.port.out;

import com.wpanther.notification.domain.exception.NotificationException;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;

/**
 * Output port: contract for sending a notification via a specific channel.
 * Implemented by EmailNotificationSenderAdapter and WebhookNotificationSenderAdapter.
 */
public interface NotificationSenderPort {

    /**
     * Send notification through the appropriate channel.
     * @throws NotificationException if sending fails
     */
    void send(Notification notification) throws NotificationException;

    /**
     * Returns true if this sender handles the given channel.
     */
    boolean supports(NotificationChannel channel);
}
```

**Step 2: Verify it compiles**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

---

### Task 4: Create input ports for REST use cases

Three interfaces for the REST adapter to depend on.

**Files:**
- Create: `src/main/java/com/wpanther/notification/application/port/in/SendNotificationUseCase.java`
- Create: `src/main/java/com/wpanther/notification/application/port/in/QueryNotificationUseCase.java`
- Create: `src/main/java/com/wpanther/notification/application/port/in/RetryNotificationUseCase.java`

**Step 1: Create SendNotificationUseCase**

```java
// src/main/java/com/wpanther/notification/application/port/in/SendNotificationUseCase.java
package com.wpanther.notification.application.port.in;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationType;

import java.util.Map;

/**
 * Input port: use case for sending notifications.
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 */
public interface SendNotificationUseCase {

    /**
     * Persist and send a fully-constructed notification synchronously.
     */
    Notification sendNotification(Notification notification);

    /**
     * Create a template-based notification and send it synchronously.
     */
    Notification createAndSend(NotificationType type, NotificationChannel channel,
                                String recipient, String templateName,
                                Map<String, Object> templateVariables);
}
```

**Step 2: Create QueryNotificationUseCase**

```java
// src/main/java/com/wpanther/notification/application/port/in/QueryNotificationUseCase.java
package com.wpanther.notification.application.port.in;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Input port: use case for querying notification state.
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 */
public interface QueryNotificationUseCase {

    Optional<Notification> findById(UUID id);

    List<Notification> findByInvoiceId(String invoiceId);

    List<Notification> findByStatus(NotificationStatus status);

    Map<String, Long> getStatistics();
}
```

**Step 3: Create RetryNotificationUseCase**

```java
// src/main/java/com/wpanther/notification/application/port/in/RetryNotificationUseCase.java
package com.wpanther.notification.application.port.in;

import java.util.UUID;

/**
 * Input port: use case for retrying a failed notification.
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 */
public interface RetryNotificationUseCase {

    /**
     * Prepare a FAILED notification for retry and dispatch it asynchronously.
     *
     * @throws java.util.NoSuchElementException  if no notification found for id
     * @throws IllegalStateException             if notification cannot be retried
     */
    void prepareAndDispatchRetry(UUID id);
}
```

**Step 4: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

---

### Task 5: Create input ports for Kafka event use cases

Three interfaces grouping the 16 Camel route event handlers by category.

**Note on dependency direction:** These interfaces take event DTO parameters from `infrastructure/messaging/`. In Phase 2 when the DTOs move to `adapter/in/kafka/`, the imports are updated. This is a documented pragmatic trade-off: event DTOs are plain data objects (no Spring/Kafka annotations).

**Files:**
- Create: `src/main/java/com/wpanther/notification/application/port/in/ProcessingEventUseCase.java`
- Create: `src/main/java/com/wpanther/notification/application/port/in/DocumentReceivedEventUseCase.java`
- Create: `src/main/java/com/wpanther/notification/application/port/in/SagaEventUseCase.java`

**Step 1: Create ProcessingEventUseCase**

```java
// src/main/java/com/wpanther/notification/application/port/in/ProcessingEventUseCase.java
package com.wpanther.notification.application.port.in;

import com.wpanther.notification.infrastructure.messaging.EbmsSentEvent;
import com.wpanther.notification.infrastructure.messaging.InvoiceProcessedEvent;
import com.wpanther.notification.infrastructure.messaging.InvoicePdfGeneratedEvent;
import com.wpanther.notification.infrastructure.messaging.PdfSignedEvent;
import com.wpanther.notification.infrastructure.messaging.TaxInvoiceProcessedEvent;
import com.wpanther.notification.infrastructure.messaging.XmlSignedEvent;

/**
 * Input port: use case for handling invoice/PDF/XML processing completion events.
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 *
 * <p>These events trigger email notifications to the configured default recipient.</p>
 */
public interface ProcessingEventUseCase {

    void handleInvoiceProcessed(InvoiceProcessedEvent event);

    void handleTaxInvoiceProcessed(TaxInvoiceProcessedEvent event);

    void handleInvoicePdfGenerated(InvoicePdfGeneratedEvent event);

    void handlePdfSigned(PdfSignedEvent event);

    void handleXmlSigned(XmlSignedEvent event);

    void handleEbmsSent(EbmsSentEvent event);
}
```

**Step 2: Create DocumentReceivedEventUseCase**

```java
// src/main/java/com/wpanther/notification/application/port/in/DocumentReceivedEventUseCase.java
package com.wpanther.notification.application.port.in;

import com.wpanther.notification.infrastructure.messaging.DocumentReceivedCountingEvent;
import com.wpanther.notification.infrastructure.messaging.DocumentReceivedEvent;

/**
 * Input port: use case for handling document received events (statistics and counting).
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 *
 * <p>These events are logged only; no email notifications are created.</p>
 */
public interface DocumentReceivedEventUseCase {

    void handleDocumentCounting(DocumentReceivedCountingEvent event);

    void handleDocumentReceived(DocumentReceivedEvent event);
}
```

**Step 3: Create SagaEventUseCase**

```java
// src/main/java/com/wpanther/notification/application/port/in/SagaEventUseCase.java
package com.wpanther.notification.application.port.in;

import com.wpanther.notification.infrastructure.messaging.saga.SagaCompletedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaFailedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaStartedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaStepCompletedEvent;

/**
 * Input port: use case for handling saga orchestration lifecycle events.
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 *
 * <p>SagaStarted and SagaStepCompleted are logged only.
 * SagaCompleted sends a completion email; SagaFailed sends an urgent failure email.</p>
 */
public interface SagaEventUseCase {

    void handleSagaStarted(SagaStartedEvent event);

    void handleSagaStepCompleted(SagaStepCompletedEvent event);

    void handleSagaCompleted(SagaCompletedEvent event);

    void handleSagaFailed(SagaFailedEvent event);
}
```

**Step 4: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

---

### Task 6: Update NotificationSendingService to use output ports

Replace `NotificationRepository` → `NotificationRepositoryPort` and `NotificationSender` → `NotificationSenderPort`. The `catch (Exception e)` block is unchanged — it already handles `NotificationException` without naming it.

**Files:**
- Modify: `src/main/java/com/wpanther/notification/application/service/NotificationSendingService.java`

**Step 1: Update imports and field types**

In `NotificationSendingService.java`, change:
```java
// REMOVE these imports:
import com.wpanther.notification.domain.repository.NotificationRepository;
import com.wpanther.notification.domain.service.NotificationSender;

// ADD these imports:
import com.wpanther.notification.application.port.out.NotificationRepositoryPort;
import com.wpanther.notification.application.port.out.NotificationSenderPort;
```

Change the field declarations:
```java
// BEFORE:
private final NotificationRepository repository;
private final List<NotificationSender> senders;

// AFTER:
private final NotificationRepositoryPort repository;
private final List<NotificationSenderPort> senders;
```

Change the constructor signature:
```java
// BEFORE:
public NotificationSendingService(NotificationRepository repository,
                                  List<NotificationSender> senders,
                                  PlatformTransactionManager txManager) {

// AFTER:
public NotificationSendingService(NotificationRepositoryPort repository,
                                  List<NotificationSenderPort> senders,
                                  PlatformTransactionManager txManager) {
```

Change the `findSender` return type:
```java
// BEFORE:
private NotificationSender findSender(NotificationChannel channel) {
    return senders.stream()
        .filter(sender -> sender.supports(channel))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No sender found for channel: " + channel));
}

// AFTER:
private NotificationSenderPort findSender(NotificationChannel channel) {
    return senders.stream()
        .filter(sender -> sender.supports(channel))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No sender found for channel: " + channel));
}
```

**Step 2: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

---

### Task 7: Update NotificationService to use output ports

Update `NotificationService` to reference `NotificationRepositoryPort` and add implementations of all 6 input port interfaces. The event handler logic moves in from `NotificationEventRoutes` (the `handleInvoiceProcessed`, `handleTaxInvoiceProcessed`, etc. private methods become public implementations of the port interfaces).

**Files:**
- Modify: `src/main/java/com/wpanther/notification/application/service/NotificationService.java`

**Step 1: Update the class declaration and imports**

Replace the entire file with:

```java
package com.wpanther.notification.application.service;

import com.wpanther.notification.application.port.in.DocumentReceivedEventUseCase;
import com.wpanther.notification.application.port.in.ProcessingEventUseCase;
import com.wpanther.notification.application.port.in.QueryNotificationUseCase;
import com.wpanther.notification.application.port.in.RetryNotificationUseCase;
import com.wpanther.notification.application.port.in.SagaEventUseCase;
import com.wpanther.notification.application.port.in.SendNotificationUseCase;
import com.wpanther.notification.application.port.out.NotificationRepositoryPort;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.infrastructure.messaging.DocumentReceivedCountingEvent;
import com.wpanther.notification.infrastructure.messaging.DocumentReceivedEvent;
import com.wpanther.notification.infrastructure.messaging.EbmsSentEvent;
import com.wpanther.notification.infrastructure.messaging.InvoiceProcessedEvent;
import com.wpanther.notification.infrastructure.messaging.InvoicePdfGeneratedEvent;
import com.wpanther.notification.infrastructure.messaging.PdfSignedEvent;
import com.wpanther.notification.infrastructure.messaging.TaxInvoiceProcessedEvent;
import com.wpanther.notification.infrastructure.messaging.XmlSignedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaCompletedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaFailedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaStartedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaStepCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing all six input port interfaces.
 *
 * <p>Thin orchestrator that delegates sending to {@link NotificationSendingService} and
 * dispatching to {@link NotificationDispatcherService}. Owns scheduled retry/pending
 * sweepers. Implements event handler use cases (previously inline in Camel routes).</p>
 *
 * <p>Dependency chain (no cycles):
 * NotificationService → NotificationDispatcherService → NotificationSendingService</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService
        implements SendNotificationUseCase,
                   QueryNotificationUseCase,
                   RetryNotificationUseCase,
                   ProcessingEventUseCase,
                   DocumentReceivedEventUseCase,
                   SagaEventUseCase {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotificationRepositoryPort repository;
    private final NotificationSendingService sendingService;
    private final NotificationDispatcherService dispatcherService;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

    @Value("${app.notification.stale-sending-timeout-ms:300000}")
    private long staleSendingTimeoutMs;

    @Value("${app.notification.default-recipient:admin@example.com}")
    private String defaultRecipient;

    // ── SendNotificationUseCase ──────────────────────────────────────────────────────────

    @Override
    public Notification sendNotification(Notification notification) {
        return sendingService.sendNotification(notification);
    }

    @Override
    public Notification createAndSend(NotificationType type, NotificationChannel channel,
                                      String recipient, String templateName,
                                      Map<String, Object> templateVariables) {
        return sendingService.createAndSend(type, channel, recipient, templateName, templateVariables);
    }

    // ── QueryNotificationUseCase ─────────────────────────────────────────────────────────

    @Override
    public Optional<Notification> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Notification> findByInvoiceId(String invoiceId) {
        return repository.findByInvoiceId(invoiceId);
    }

    @Override
    public List<Notification> findByStatus(NotificationStatus status) {
        return repository.findByStatus(status);
    }

    @Override
    public Map<String, Long> getStatistics() {
        return sendingService.getStatistics();
    }

    // ── RetryNotificationUseCase ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void prepareAndDispatchRetry(UUID id) {
        Notification notification = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Notification not found: " + id));

        if (!notification.canRetry(maxRetries)) {
            throw new IllegalStateException("Cannot retry notification");
        }

        notification.prepareRetry();
        repository.save(notification);
        dispatcherService.dispatchAsync(notification);
    }

    // ── ProcessingEventUseCase ────────────────────────────────────────────────────────────

    @Override
    public void handleInvoiceProcessed(InvoiceProcessedEvent event) {
        log.info("Processing InvoiceProcessedEvent: invoiceId={}, invoiceNumber={}",
            event.getInvoiceId(), event.getInvoiceNumber());

        Map<String, Object> vars = new HashMap<>();
        vars.put("invoiceId", event.getInvoiceId());
        vars.put("invoiceNumber", event.getInvoiceNumber());
        vars.put("totalAmount", String.format("%,.2f", event.getTotalAmount()));
        vars.put("currency", event.getCurrency());
        vars.put("processedAt", formatInstant(event.getOccurredAt()));

        Notification notification = Notification.createFromTemplate(
            NotificationType.INVOICE_PROCESSED, NotificationChannel.EMAIL,
            defaultRecipient, "invoice-processed", vars);
        notification.setSubject("Invoice Processed: " + event.getInvoiceNumber());
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());

        dispatcherService.dispatchAsync(notification);
    }

    @Override
    public void handleTaxInvoiceProcessed(TaxInvoiceProcessedEvent event) {
        log.info("Processing TaxInvoiceProcessedEvent: invoiceId={}, invoiceNumber={}",
            event.getInvoiceId(), event.getInvoiceNumber());

        Map<String, Object> vars = new HashMap<>();
        vars.put("invoiceId", event.getInvoiceId());
        vars.put("invoiceNumber", event.getInvoiceNumber());
        vars.put("totalAmount", String.format("%,.2f", event.getTotal()));
        vars.put("currency", event.getCurrency());
        vars.put("processedAt", formatInstant(event.getOccurredAt()));

        Notification notification = Notification.createFromTemplate(
            NotificationType.TAXINVOICE_PROCESSED, NotificationChannel.EMAIL,
            defaultRecipient, "taxinvoice-processed", vars);
        notification.setSubject("Tax Invoice Processed: " + event.getInvoiceNumber());
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());

        dispatcherService.dispatchAsync(notification);
    }

    @Override
    public void handleInvoicePdfGenerated(InvoicePdfGeneratedEvent event) {
        log.info("Processing InvoicePdfGeneratedEvent: invoiceId={}, invoiceNumber={}",
            event.getInvoiceId(), event.getInvoiceNumber());

        Map<String, Object> vars = new HashMap<>();
        vars.put("invoiceId", event.getInvoiceId());
        vars.put("invoiceNumber", event.getInvoiceNumber());
        vars.put("documentId", event.getDocumentId());
        vars.put("documentUrl", event.getDocumentUrl());
        vars.put("fileSize", formatFileSize(event.getFileSize()));
        vars.put("generatedAt", formatInstant(event.getOccurredAt()));
        vars.put("xmlEmbedded", event.isXmlEmbedded());
        vars.put("digitallySigned", event.isDigitallySigned());

        Notification notification = Notification.createFromTemplate(
            NotificationType.PDF_GENERATED, NotificationChannel.EMAIL,
            defaultRecipient, "pdf-generated", vars);
        notification.setSubject("PDF Invoice Ready: " + event.getInvoiceNumber());
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("documentUrl", event.getDocumentUrl());
        notification.addMetadata("documentId", event.getDocumentId());

        dispatcherService.dispatchAsync(notification);
    }

    @Override
    public void handlePdfSigned(PdfSignedEvent event) {
        log.info("Processing PdfSignedEvent: invoiceId={}, invoiceNumber={}, documentType={}",
            event.getInvoiceId(), event.getInvoiceNumber(), event.getDocumentType());

        Map<String, Object> vars = new HashMap<>();
        vars.put("invoiceId", event.getInvoiceId());
        vars.put("invoiceNumber", event.getInvoiceNumber());
        vars.put("documentType", event.getDocumentType());
        vars.put("signedDocumentId", event.getSignedDocumentId());
        vars.put("signedPdfUrl", event.getSignedPdfUrl());
        vars.put("signedPdfSize", formatFileSize(event.getSignedPdfSize()));
        vars.put("transactionId", event.getTransactionId());
        vars.put("signatureLevel", event.getSignatureLevel());
        vars.put("signatureTimestamp", formatInstant(event.getSignatureTimestamp()));

        Notification notification = Notification.createFromTemplate(
            NotificationType.PDF_SIGNED, NotificationChannel.EMAIL,
            defaultRecipient, "pdf-signed", vars);
        notification.setSubject("PDF Invoice Signed: " + event.getInvoiceNumber());
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("signedPdfUrl", event.getSignedPdfUrl());
        notification.addMetadata("signedDocumentId", event.getSignedDocumentId());
        notification.addMetadata("signatureLevel", event.getSignatureLevel());

        dispatcherService.dispatchAsync(notification);
    }

    @Override
    public void handleXmlSigned(XmlSignedEvent event) {
        log.info("Processing XmlSignedEvent: invoiceId={}, invoiceNumber={}, documentType={}",
            event.getInvoiceId(), event.getInvoiceNumber(), event.getDocumentType());

        Map<String, Object> vars = new HashMap<>();
        vars.put("invoiceId", event.getInvoiceId());
        vars.put("invoiceNumber", event.getInvoiceNumber());
        vars.put("documentType", event.getDocumentType());
        vars.put("signedAt", formatInstant(event.getOccurredAt()));

        Notification notification = Notification.createFromTemplate(
            NotificationType.XML_SIGNED, NotificationChannel.EMAIL,
            defaultRecipient, "xml-signed", vars);
        notification.setSubject("XML Document Signed: " + event.getInvoiceNumber());
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("documentType", event.getDocumentType());

        dispatcherService.dispatchAsync(notification);
    }

    @Override
    public void handleEbmsSent(EbmsSentEvent event) {
        log.info("Processing EbmsSentEvent: documentId={}, documentType={}, ebmsMessageId={}",
            event.getDocumentId(), event.getDocumentType(), event.getEbmsMessageId());

        Map<String, Object> vars = new HashMap<>();
        vars.put("documentId", event.getDocumentId());
        vars.put("invoiceId", event.getInvoiceId() != null ? event.getInvoiceId() : "N/A");
        vars.put("invoiceNumber", event.getInvoiceNumber() != null ? event.getInvoiceNumber() : "N/A");
        vars.put("documentType", event.getDocumentType());
        vars.put("ebmsMessageId", event.getEbmsMessageId());
        vars.put("sentAt", formatInstant(event.getSentAt()));
        vars.put("correlationId", event.getCorrelationId());

        String displayNumber = event.getInvoiceNumber() != null ? event.getInvoiceNumber() : event.getDocumentId();

        Notification notification = Notification.createFromTemplate(
            NotificationType.EBMS_SENT, NotificationChannel.EMAIL,
            defaultRecipient, "ebms-sent", vars);
        notification.setSubject("Document Submitted to TRD: " + displayNumber);
        notification.setInvoiceId(event.getInvoiceId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("ebmsMessageId", event.getEbmsMessageId());
        notification.addMetadata("documentType", event.getDocumentType());

        dispatcherService.dispatchAsync(notification);
    }

    // ── DocumentReceivedEventUseCase ─────────────────────────────────────────────────────

    @Override
    public void handleDocumentCounting(DocumentReceivedCountingEvent event) {
        log.info("Processing DocumentReceivedCountingEvent: documentId={}, correlationId={}",
            event.getDocumentId(), event.getCorrelationId());
        // Log only. Future: persist to database for total received count statistics.
    }

    @Override
    public void handleDocumentReceived(DocumentReceivedEvent event) {
        log.info("Processing DocumentReceivedEvent (statistics): documentId={}, documentType={}, correlationId={}",
            event.getDocumentId(), event.getDocumentType(), event.getCorrelationId());
        // Log only. Future: persist to database for type-specific statistics.
    }

    // ── SagaEventUseCase ─────────────────────────────────────────────────────────────────

    @Override
    public void handleSagaStarted(SagaStartedEvent event) {
        log.info("Saga started: sagaId={}, documentType={}, invoiceNumber={}",
            event.getSagaId(), event.getDocumentType(), event.getInvoiceNumber());
        // Log only — no notification created.
    }

    @Override
    public void handleSagaStepCompleted(SagaStepCompletedEvent event) {
        log.info("Saga step completed: sagaId={}, step={}, nextStep={}",
            event.getSagaId(), event.getCompletedStep(), event.getNextStep());
        // Log only — no notification created.
    }

    @Override
    public void handleSagaCompleted(SagaCompletedEvent event) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("sagaId", event.getSagaId());
        vars.put("documentId", event.getDocumentId());
        vars.put("invoiceNumber", event.getInvoiceNumber() != null ? event.getInvoiceNumber() : "N/A");
        vars.put("documentType", event.getDocumentType());
        vars.put("stepsExecuted", event.getStepsExecuted());
        vars.put("durationMs", event.getDurationMs());
        vars.put("durationSec", String.format("%.2f", event.getDurationMs() / 1000.0));
        vars.put("completedAt", formatInstant(event.getCompletedAt()));

        Notification notification = Notification.createFromTemplate(
            NotificationType.SAGA_COMPLETED, NotificationChannel.EMAIL,
            defaultRecipient, "saga-completed", vars);
        notification.setSubject("Saga Completed: " +
            (event.getInvoiceNumber() != null ? event.getInvoiceNumber() : event.getDocumentId()));
        notification.setInvoiceId(event.getDocumentId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("sagaId", event.getSagaId());

        dispatcherService.dispatchAsync(notification);
    }

    @Override
    public void handleSagaFailed(SagaFailedEvent event) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("sagaId", event.getSagaId());
        vars.put("documentId", event.getDocumentId());
        vars.put("invoiceNumber", event.getInvoiceNumber() != null ? event.getInvoiceNumber() : "N/A");
        vars.put("documentType", event.getDocumentType());
        vars.put("failedStep", event.getFailedStep());
        vars.put("errorMessage", event.getErrorMessage());
        vars.put("retryCount", event.getRetryCount());
        vars.put("compensationInitiated",
            event.getCompensationInitiated() != null && event.getCompensationInitiated());
        vars.put("failedAt", formatInstant(event.getFailedAt()));

        Notification notification = Notification.createFromTemplate(
            NotificationType.SAGA_FAILED, NotificationChannel.EMAIL,
            defaultRecipient, "saga-failed", vars);
        notification.setSubject("URGENT: Saga Failed - " +
            (event.getInvoiceNumber() != null ? event.getInvoiceNumber() : event.getDocumentId()));
        notification.setInvoiceId(event.getDocumentId());
        notification.setInvoiceNumber(event.getInvoiceNumber());
        notification.setCorrelationId(event.getCorrelationId());
        notification.addMetadata("sagaId", event.getSagaId());
        notification.addMetadata("failedStep", event.getFailedStep());

        dispatcherService.dispatchAsync(notification);
    }

    // ── Scheduled sweepers ────────────────────────────────────────────────────────────────

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
    @Transactional
    public void retryFailedNotifications() {
        List<Notification> failedNotifications = repository.findFailedNotifications(maxRetries, 100);

        log.info("Found {} failed notifications to retry", failedNotifications.size());

        for (Notification notification : failedNotifications) {
            try {
                log.info("Retrying notification: id={}, attempt={}",
                    notification.getId(), notification.getRetryCount() + 1);
                notification.prepareRetry();
                notification = repository.save(notification);
                dispatcherService.dispatchAsync(notification);
            } catch (Exception e) {
                log.error("Failed to retry notification: id={}", notification.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.notification.processing-interval:60000}")
    @Transactional
    public void processPendingNotifications() {
        List<Notification> pendingNotifications = repository.findPendingNotifications(100);

        log.debug("Processing {} pending notifications", pendingNotifications.size());

        for (Notification notification : pendingNotifications) {
            try {
                dispatcherService.dispatchAsync(notification);
            } catch (Exception e) {
                log.error("Failed to process pending notification: id={}", notification.getId(), e);
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────────────────

    private String formatInstant(Instant instant) {
        return instant != null
            ? DATE_FORMATTER.format(instant.atZone(ZoneId.systemDefault()))
            : "N/A";
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
```

**Step 2: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

---

### Task 8: Update NotificationRepositoryImpl to implement NotificationRepositoryPort

**Files:**
- Modify: `src/main/java/com/wpanther/notification/infrastructure/persistence/NotificationRepositoryImpl.java`

**Step 1: Update implements clause and import**

At the top of the file, change:
```java
// REMOVE:
import com.wpanther.notification.domain.repository.NotificationRepository;

// ADD:
import com.wpanther.notification.application.port.out.NotificationRepositoryPort;
```

Change the class declaration:
```java
// BEFORE:
public class NotificationRepositoryImpl implements NotificationRepository {

// AFTER:
public class NotificationRepositoryImpl implements NotificationRepositoryPort {
```

**Step 2: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

---

### Task 9: Update notification senders to implement NotificationSenderPort

Both `EmailNotificationSender` and `WebhookNotificationSender` currently implement `NotificationSender` (old domain service interface) and throw `NotificationSender.NotificationException`. Update them to implement `NotificationSenderPort` and throw the top-level `NotificationException`.

**Files:**
- Modify: `src/main/java/com/wpanther/notification/infrastructure/notification/EmailNotificationSender.java`
- Modify: `src/main/java/com/wpanther/notification/infrastructure/notification/WebhookNotificationSender.java`

**Step 1: Update EmailNotificationSender**

In `EmailNotificationSender.java`, change:
```java
// REMOVE:
import com.wpanther.notification.domain.service.NotificationSender;

// ADD:
import com.wpanther.notification.application.port.out.NotificationSenderPort;
import com.wpanther.notification.domain.exception.NotificationException;
```

Change the class declaration:
```java
// BEFORE:
public class EmailNotificationSender implements NotificationSender {

// AFTER:
public class EmailNotificationSender implements NotificationSenderPort {
```

Change every `throws NotificationSender.NotificationException` in the method signatures to `throws NotificationException`.

Change every `throw new NotificationSender.NotificationException(...)` in the method body to `throw new NotificationException(...)`.

**Step 2: Update WebhookNotificationSender**

Apply the same changes to `WebhookNotificationSender.java`:
```java
// REMOVE:
import com.wpanther.notification.domain.service.NotificationSender;

// ADD:
import com.wpanther.notification.application.port.out.NotificationSenderPort;
import com.wpanther.notification.domain.exception.NotificationException;
```

Change `implements NotificationSender` → `implements NotificationSenderPort`.
Change all `NotificationSender.NotificationException` references → `NotificationException`.

**Step 3: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

---

### Task 10: Thin out NotificationEventRoutes (inject port interfaces)

Replace the current `NotificationService + NotificationDispatcherService` dependencies with the three event use case port interfaces. Route handler methods become 2-line delegates: extract body → call use case.

**Files:**
- Modify: `src/main/java/com/wpanther/notification/infrastructure/messaging/NotificationEventRoutes.java`

**Step 1: Replace the file contents**

```java
package com.wpanther.notification.infrastructure.messaging;

import com.wpanther.notification.application.port.in.DocumentReceivedEventUseCase;
import com.wpanther.notification.application.port.in.ProcessingEventUseCase;
import com.wpanther.notification.application.port.in.SagaEventUseCase;
import com.wpanther.notification.infrastructure.config.KafkaTopicsConfig;
import com.wpanther.notification.infrastructure.messaging.saga.SagaCompletedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaFailedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaStartedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaStepCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for consuming Kafka notification events.
 *
 * <p>Each route is intentionally thin: unmarshal JSON → delegate to the appropriate
 * input port interface. All business logic lives in the application service layer.</p>
 */
@Component
@Slf4j
public class NotificationEventRoutes extends RouteBuilder {

    private final ProcessingEventUseCase processingEventUseCase;
    private final DocumentReceivedEventUseCase documentReceivedEventUseCase;
    private final SagaEventUseCase sagaEventUseCase;
    private final boolean notificationEnabled;
    private final String consumerGroup;
    private final String kafkaBrokers;
    private final KafkaTopicsConfig topics;

    public NotificationEventRoutes(
            ProcessingEventUseCase processingEventUseCase,
            DocumentReceivedEventUseCase documentReceivedEventUseCase,
            SagaEventUseCase sagaEventUseCase,
            @Value("${app.notification.enabled:true}") boolean notificationEnabled,
            @Value("${spring.kafka.consumer.group-id}") String consumerGroup,
            @Value("${spring.kafka.bootstrap-servers}") String kafkaBrokers,
            KafkaTopicsConfig topics) {
        this.processingEventUseCase = processingEventUseCase;
        this.documentReceivedEventUseCase = documentReceivedEventUseCase;
        this.sagaEventUseCase = sagaEventUseCase;
        this.notificationEnabled = notificationEnabled;
        this.consumerGroup = consumerGroup;
        this.kafkaBrokers = kafkaBrokers;
        this.topics = topics;
    }

    @Override
    public void configure() throws Exception {
        errorHandler(deadLetterChannel("kafka:" + topics.notificationDlq() + "?brokers=" + kafkaBrokers)
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(30000)
            .logExhausted(true)
            .logRetryAttempted(true)
            .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));

        String kafkaOptions = "?brokers=" + kafkaBrokers
            + "&groupId=" + consumerGroup
            + "&autoOffsetReset=earliest"
            + "&autoCommitEnable=false"
            + "&breakOnFirstError=true";

        from("kafka:" + topics.invoiceProcessed() + kafkaOptions)
            .routeId("notification-invoice-processed")
            .log("Received InvoiceProcessedEvent from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, InvoiceProcessedEvent.class)
            .process(this::routeInvoiceProcessed)
            .log("Processed InvoiceProcessedEvent: ${header.invoiceNumber}");

        from("kafka:" + topics.taxinvoiceProcessed() + kafkaOptions)
            .routeId("notification-taxinvoice-processed")
            .log("Received TaxInvoiceProcessedEvent from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, TaxInvoiceProcessedEvent.class)
            .process(this::routeTaxInvoiceProcessed)
            .log("Processed TaxInvoiceProcessedEvent: ${header.invoiceNumber}");

        from("kafka:" + topics.pdfGenerated() + kafkaOptions)
            .routeId("notification-pdf-generated")
            .log("Received InvoicePdfGeneratedEvent from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, InvoicePdfGeneratedEvent.class)
            .process(this::routePdfGenerated)
            .log("Processed InvoicePdfGeneratedEvent: ${header.invoiceNumber}");

        from("kafka:" + topics.pdfSigned() + kafkaOptions)
            .routeId("notification-pdf-signed")
            .log("Received PdfSignedEvent from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, PdfSignedEvent.class)
            .process(this::routePdfSigned)
            .log("Processed PdfSignedEvent: ${header.invoiceNumber}");

        from("kafka:" + topics.xmlSigned() + kafkaOptions)
            .routeId("notification-xml-signed")
            .log("Received XmlSignedEvent from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, XmlSignedEvent.class)
            .process(this::routeXmlSigned)
            .log("Processed XmlSignedEvent: ${header.invoiceNumber}");

        from("kafka:" + topics.documentReceived() + kafkaOptions)
            .routeId("notification-document-counting")
            .log("Received DocumentReceivedCountingEvent from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedCountingEvent.class)
            .process(this::routeDocumentCounting)
            .log("Processed DocumentReceivedCountingEvent: documentId=${header.documentId}");

        from("kafka:" + topics.taxInvoiceReceived() + kafkaOptions)
            .routeId("notification-tax-invoice-received")
            .log("Received DocumentReceivedEvent (TAX_INVOICE) from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedEvent.class)
            .process(this::routeDocumentReceived)
            .log("Processed DocumentReceivedEvent (TAX_INVOICE)");

        from("kafka:" + topics.invoiceReceived() + kafkaOptions)
            .routeId("notification-invoice-received")
            .log("Received DocumentReceivedEvent (INVOICE) from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedEvent.class)
            .process(this::routeDocumentReceived)
            .log("Processed DocumentReceivedEvent (INVOICE)");

        from("kafka:" + topics.receiptReceived() + kafkaOptions)
            .routeId("notification-receipt-received")
            .log("Received DocumentReceivedEvent (RECEIPT) from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedEvent.class)
            .process(this::routeDocumentReceived)
            .log("Processed DocumentReceivedEvent (RECEIPT)");

        from("kafka:" + topics.debitCreditNoteReceived() + kafkaOptions)
            .routeId("notification-debit-credit-note-received")
            .log("Received DocumentReceivedEvent (DEBIT_CREDIT_NOTE) from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedEvent.class)
            .process(this::routeDocumentReceived)
            .log("Processed DocumentReceivedEvent (DEBIT_CREDIT_NOTE)");

        from("kafka:" + topics.cancellationReceived() + kafkaOptions)
            .routeId("notification-cancellation-received")
            .log("Received DocumentReceivedEvent (CANCELLATION) from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedEvent.class)
            .process(this::routeDocumentReceived)
            .log("Processed DocumentReceivedEvent (CANCELLATION)");

        from("kafka:" + topics.abbreviatedReceived() + kafkaOptions)
            .routeId("notification-abbreviated-received")
            .log("Received DocumentReceivedEvent (ABBREVIATED) from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedEvent.class)
            .process(this::routeDocumentReceived)
            .log("Processed DocumentReceivedEvent (ABBREVIATED)");

        from("kafka:" + topics.ebmsSent() + kafkaOptions)
            .routeId("notification-ebms-sent")
            .log("Received EbmsSentEvent from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, EbmsSentEvent.class)
            .process(this::routeEbmsSent)
            .log("Processed EbmsSentEvent: ${header.invoiceNumber}");

        from("kafka:" + topics.sagaLifecycleStarted() + kafkaOptions)
            .routeId("notification-saga-started")
            .log("Received SagaStartedEvent from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, SagaStartedEvent.class)
            .process(this::routeSagaStarted)
            .log("Processed SagaStartedEvent: sagaId=${header.sagaId}");

        from("kafka:" + topics.sagaLifecycleStepCompleted() + kafkaOptions)
            .routeId("notification-saga-step-completed")
            .log("Received SagaStepCompletedEvent from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, SagaStepCompletedEvent.class)
            .process(this::routeSagaStepCompleted)
            .log("Processed SagaStepCompletedEvent: step=${header.completedStep}");

        from("kafka:" + topics.sagaLifecycleCompleted() + kafkaOptions)
            .routeId("notification-saga-completed")
            .log("Received SagaCompletedEvent from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, SagaCompletedEvent.class)
            .process(this::routeSagaCompleted)
            .log("Processed SagaCompletedEvent: sagaId=${header.sagaId}");

        from("kafka:" + topics.sagaLifecycleFailed() + kafkaOptions)
            .routeId("notification-saga-failed")
            .log("Received SagaFailedEvent from Kafka")
            .choice().when(exchange -> !notificationEnabled).log("Notifications disabled").stop().end()
            .unmarshal().json(JsonLibrary.Jackson, SagaFailedEvent.class)
            .process(this::routeSagaFailed)
            .log("Processed SagaFailedEvent: sagaId=${header.sagaId}");
    }

    // ── Thin route delegates (unmarshal + forward to use case + set logging headers) ─────

    private void routeInvoiceProcessed(Exchange exchange) {
        InvoiceProcessedEvent event = exchange.getIn().getBody(InvoiceProcessedEvent.class);
        processingEventUseCase.handleInvoiceProcessed(event);
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    private void routeTaxInvoiceProcessed(Exchange exchange) {
        TaxInvoiceProcessedEvent event = exchange.getIn().getBody(TaxInvoiceProcessedEvent.class);
        processingEventUseCase.handleTaxInvoiceProcessed(event);
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    private void routePdfGenerated(Exchange exchange) {
        InvoicePdfGeneratedEvent event = exchange.getIn().getBody(InvoicePdfGeneratedEvent.class);
        processingEventUseCase.handleInvoicePdfGenerated(event);
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    private void routePdfSigned(Exchange exchange) {
        PdfSignedEvent event = exchange.getIn().getBody(PdfSignedEvent.class);
        processingEventUseCase.handlePdfSigned(event);
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    private void routeXmlSigned(Exchange exchange) {
        XmlSignedEvent event = exchange.getIn().getBody(XmlSignedEvent.class);
        processingEventUseCase.handleXmlSigned(event);
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }

    private void routeDocumentCounting(Exchange exchange) {
        DocumentReceivedCountingEvent event = exchange.getIn().getBody(DocumentReceivedCountingEvent.class);
        documentReceivedEventUseCase.handleDocumentCounting(event);
        exchange.getIn().setHeader("documentId", event.getDocumentId());
        exchange.getIn().setHeader("correlationId", event.getCorrelationId());
    }

    private void routeDocumentReceived(Exchange exchange) {
        DocumentReceivedEvent event = exchange.getIn().getBody(DocumentReceivedEvent.class);
        documentReceivedEventUseCase.handleDocumentReceived(event);
        exchange.getIn().setHeader("documentId", event.getDocumentId());
        exchange.getIn().setHeader("documentType", event.getDocumentType());
    }

    private void routeEbmsSent(Exchange exchange) {
        EbmsSentEvent event = exchange.getIn().getBody(EbmsSentEvent.class);
        processingEventUseCase.handleEbmsSent(event);
        String displayNumber = event.getInvoiceNumber() != null ? event.getInvoiceNumber() : event.getDocumentId();
        exchange.getIn().setHeader("invoiceNumber", displayNumber);
    }

    private void routeSagaStarted(Exchange exchange) {
        SagaStartedEvent event = exchange.getIn().getBody(SagaStartedEvent.class);
        sagaEventUseCase.handleSagaStarted(event);
        exchange.getIn().setHeader("sagaId", event.getSagaId());
    }

    private void routeSagaStepCompleted(Exchange exchange) {
        SagaStepCompletedEvent event = exchange.getIn().getBody(SagaStepCompletedEvent.class);
        sagaEventUseCase.handleSagaStepCompleted(event);
        exchange.getIn().setHeader("completedStep", event.getCompletedStep());
    }

    private void routeSagaCompleted(Exchange exchange) {
        SagaCompletedEvent event = exchange.getIn().getBody(SagaCompletedEvent.class);
        sagaEventUseCase.handleSagaCompleted(event);
        exchange.getIn().setHeader("sagaId", event.getSagaId());
    }

    private void routeSagaFailed(Exchange exchange) {
        SagaFailedEvent event = exchange.getIn().getBody(SagaFailedEvent.class);
        sagaEventUseCase.handleSagaFailed(event);
        exchange.getIn().setHeader("sagaId", event.getSagaId());
    }
}
```

**Step 2: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

---

### Task 11: Update NotificationController to inject port interfaces

The controller currently injects `NotificationService` (concrete). Replace it with the three use case port interfaces it actually needs.

**Files:**
- Modify: `src/main/java/com/wpanther/notification/application/controller/NotificationController.java`

**Step 1: Replace service import and field**

Change imports:
```java
// REMOVE:
import com.wpanther.notification.application.service.NotificationService;

// ADD:
import com.wpanther.notification.application.port.in.QueryNotificationUseCase;
import com.wpanther.notification.application.port.in.RetryNotificationUseCase;
import com.wpanther.notification.application.port.in.SendNotificationUseCase;
```

Replace the single field injection:
```java
// BEFORE:
private final NotificationService notificationService;

// AFTER:
private final SendNotificationUseCase sendNotificationUseCase;
private final QueryNotificationUseCase queryNotificationUseCase;
private final RetryNotificationUseCase retryNotificationUseCase;
```

**Step 2: Update method call sites**

In `sendNotification()`:
```java
// BEFORE:
notification = notificationService.sendNotification(notification);

// AFTER:
notification = sendNotificationUseCase.sendNotification(notification);
```

In `getNotification()`:
```java
// BEFORE:
return notificationService.findById(id)

// AFTER:
return queryNotificationUseCase.findById(id)
```

In `getNotificationsByInvoice()`:
```java
// BEFORE:
return ResponseEntity.ok(notificationService.findByInvoiceId(invoiceId));

// AFTER:
return ResponseEntity.ok(queryNotificationUseCase.findByInvoiceId(invoiceId));
```

In `getNotificationsByStatus()`:
```java
// BEFORE:
return ResponseEntity.ok(notificationService.findByStatus(status));

// AFTER:
return ResponseEntity.ok(queryNotificationUseCase.findByStatus(status));
```

In `getStatistics()`:
```java
// BEFORE:
return ResponseEntity.ok(notificationService.getStatistics());

// AFTER:
return ResponseEntity.ok(queryNotificationUseCase.getStatistics());
```

In `retryNotification()`:
```java
// BEFORE:
notificationService.prepareAndDispatchRetry(id);

// AFTER:
retryNotificationUseCase.prepareAndDispatchRetry(id);
```

**Step 3: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

---

### Task 12: Update affected test files

Four test files reference old interfaces or concrete class injections. Update each one.

**Files:**
- Modify: `src/test/java/com/wpanther/notification/application/service/NotificationServiceTest.java`
- Modify: `src/test/java/com/wpanther/notification/application/service/NotificationSendingServiceTest.java`
- Modify: `src/test/java/com/wpanther/notification/application/controller/NotificationControllerTest.java`
- Modify: `src/test/java/com/wpanther/notification/infrastructure/messaging/NotificationEventRoutesTest.java`
- Modify: `src/test/java/com/wpanther/notification/infrastructure/persistence/NotificationRepositoryImplTest.java`
- Modify (possibly): `src/test/java/com/wpanther/notification/infrastructure/notification/EmailNotificationSenderTest.java`
- Modify (possibly): `src/test/java/com/wpanther/notification/infrastructure/notification/WebhookNotificationSenderTest.java`

**Step 1: Update NotificationServiceTest.java**

Change:
```java
// REMOVE:
import com.wpanther.notification.domain.repository.NotificationRepository;

// ADD:
import com.wpanther.notification.application.port.out.NotificationRepositoryPort;
```

Change the `@Mock` declaration:
```java
// BEFORE:
@Mock
private NotificationRepository repository;

// AFTER:
@Mock
private NotificationRepositoryPort repository;
```

**Step 2: Update NotificationSendingServiceTest.java**

Change:
```java
// REMOVE:
import com.wpanther.notification.domain.repository.NotificationRepository;
import com.wpanther.notification.domain.service.NotificationSender;

// ADD:
import com.wpanther.notification.application.port.out.NotificationRepositoryPort;
import com.wpanther.notification.application.port.out.NotificationSenderPort;
```

Change the `@Mock` declarations:
```java
// BEFORE:
@Mock
private NotificationRepository repository;
@Mock
private NotificationSender emailSender;

// AFTER:
@Mock
private NotificationRepositoryPort repository;
@Mock
private NotificationSenderPort emailSender;
```

Also update any `List<NotificationSender>` → `List<NotificationSenderPort>` and any `NotificationSender.NotificationException` → `NotificationException` (import from `com.wpanther.notification.domain.exception`).

**Step 3: Update NotificationControllerTest.java**

The controller test uses `@WebMvcTest` with `@MockBean NotificationService`. Now the controller injects 3 port interfaces, so the test needs 3 mock beans:

```java
// REMOVE:
import com.wpanther.notification.application.service.NotificationService;
@MockBean
private NotificationService notificationService;

// ADD:
import com.wpanther.notification.application.port.in.QueryNotificationUseCase;
import com.wpanther.notification.application.port.in.RetryNotificationUseCase;
import com.wpanther.notification.application.port.in.SendNotificationUseCase;

@MockBean
private SendNotificationUseCase sendNotificationUseCase;

@MockBean
private QueryNotificationUseCase queryNotificationUseCase;

@MockBean
private RetryNotificationUseCase retryNotificationUseCase;
```

Update all `when(notificationService....)` / `verify(notificationService....)` calls to use the correct mock variable. Example:
```java
// BEFORE:
when(notificationService.sendNotification(any())).thenReturn(notification);
when(notificationService.findById(notificationId)).thenReturn(Optional.of(notification));
when(notificationService.getStatistics()).thenReturn(stats);
doThrow(new NoSuchElementException()).when(notificationService).prepareAndDispatchRetry(any());

// AFTER:
when(sendNotificationUseCase.sendNotification(any())).thenReturn(notification);
when(queryNotificationUseCase.findById(notificationId)).thenReturn(Optional.of(notification));
when(queryNotificationUseCase.getStatistics()).thenReturn(stats);
doThrow(new NoSuchElementException()).when(retryNotificationUseCase).prepareAndDispatchRetry(any());
```

**Step 4: Update NotificationEventRoutesTest.java**

The routes test currently mocks `NotificationService` and `NotificationDispatcherService`. After the refactor, the routes inject 3 event use case port interfaces:

```java
// REMOVE:
import com.wpanther.notification.application.service.NotificationDispatcherService;
import com.wpanther.notification.application.service.NotificationService;

@MockBean
private NotificationService notificationService;

@MockBean
private NotificationDispatcherService dispatcherService;

// ADD:
import com.wpanther.notification.application.port.in.DocumentReceivedEventUseCase;
import com.wpanther.notification.application.port.in.ProcessingEventUseCase;
import com.wpanther.notification.application.port.in.SagaEventUseCase;

@MockBean
private ProcessingEventUseCase processingEventUseCase;

@MockBean
private DocumentReceivedEventUseCase documentReceivedEventUseCase;

@MockBean
private SagaEventUseCase sagaEventUseCase;
```

Update any `verify(notificationService...)` / `verify(dispatcherService...)` calls to use the correct mock. For example, a test that verifies invoice processing:
```java
// BEFORE:
verify(dispatcherService).dispatchAsync(any(Notification.class));

// AFTER:
verify(processingEventUseCase).handleInvoiceProcessed(any(InvoiceProcessedEvent.class));
```

**Step 5: Update NotificationRepositoryImplTest.java**

Change the `implements` reference in the test's assertion or cast (if any):
```java
// If the test has:
import com.wpanther.notification.domain.repository.NotificationRepository;
// Remove that and add nothing (the impl's interface is now NotificationRepositoryPort)
```

**Step 6: Update EmailNotificationSenderTest and WebhookNotificationSenderTest**

If either test references `NotificationSender.NotificationException`:
```java
// REMOVE:
import com.wpanther.notification.domain.service.NotificationSender;
// ... any usage of NotificationSender.NotificationException

// ADD:
import com.wpanther.notification.domain.exception.NotificationException;
// Replace NotificationSender.NotificationException → NotificationException
```

**Step 7: Run unit tests**

```bash
mvn test
```
Expected: BUILD SUCCESS, 147 tests passing.

---

### Task 13: Delete old domain interfaces and commit Phase 1

**Files:**
- Delete: `src/main/java/com/wpanther/notification/domain/repository/NotificationRepository.java`
- Delete: `src/main/java/com/wpanther/notification/domain/service/NotificationSender.java`
- Delete: `src/main/java/com/wpanther/notification/domain/repository/` (empty dir)
- Delete: `src/main/java/com/wpanther/notification/domain/service/` (empty dir)

**Step 1: Delete the old interfaces**

```bash
rm src/main/java/com/wpanther/notification/domain/repository/NotificationRepository.java
rm -rf src/main/java/com/wpanther/notification/domain/repository/
rm src/main/java/com/wpanther/notification/domain/service/NotificationSender.java
rm -rf src/main/java/com/wpanther/notification/domain/service/
```

**Step 2: Run tests to confirm nothing broke**

```bash
mvn test
```
Expected: BUILD SUCCESS, 147 tests passing.

**Step 3: Commit Phase 1**

```bash
git add -A
git commit -m "Add hexagonal port interfaces and thin Camel routes (Phase 1)"
```

---

## Phase 2: Rename Packages to adapter/in|out

### Task 14: Move persistence layer → adapter/out/persistence

Move all files from `infrastructure/persistence/` to `adapter/out/persistence/`. Rename `NotificationRepositoryImpl` → `NotificationRepositoryAdapter`.

**Files moved and renamed:**
- `infrastructure/persistence/NotificationRepositoryImpl.java` → `adapter/out/persistence/NotificationRepositoryAdapter.java`
- `infrastructure/persistence/NotificationEntity.java` → `adapter/out/persistence/NotificationEntity.java`
- `infrastructure/persistence/JpaNotificationRepository.java` → `adapter/out/persistence/JpaNotificationRepository.java`
- `infrastructure/persistence/JsonMapConverter.java` → `adapter/out/persistence/JsonMapConverter.java`
- `infrastructure/persistence/outbox/OutboxEventEntity.java` → `adapter/out/persistence/outbox/OutboxEventEntity.java`
- `infrastructure/persistence/outbox/SpringDataOutboxRepository.java` → `adapter/out/persistence/outbox/SpringDataOutboxRepository.java`
- `infrastructure/persistence/outbox/JpaOutboxEventRepository.java` → `adapter/out/persistence/outbox/JpaOutboxEventRepository.java`

**Step 1: Create new directories**

```bash
mkdir -p src/main/java/com/wpanther/notification/adapter/out/persistence/outbox
```

**Step 2: Move and update each file**

For each file: (a) copy to new location, (b) update package declaration, (c) delete original.

For `NotificationRepositoryAdapter.java` (renamed from `NotificationRepositoryImpl`):
- New package: `package com.wpanther.notification.adapter.out.persistence;`
- Class name: `NotificationRepositoryAdapter` (rename)
- All other code: identical to `NotificationRepositoryImpl`

For all other persistence files, just update the package:
- Old: `package com.wpanther.notification.infrastructure.persistence;`
- New: `package com.wpanther.notification.adapter.out.persistence;`

For outbox files:
- Old: `package com.wpanther.notification.infrastructure.persistence.outbox;`
- New: `package com.wpanther.notification.adapter.out.persistence.outbox;`

**Step 3: Update references to NotificationRepositoryImpl in all files**

Search for any remaining references to `NotificationRepositoryImpl`:
```bash
grep -r "NotificationRepositoryImpl" src/
```
If found, update to `NotificationRepositoryAdapter`.

**Step 4: Delete original infrastructure/persistence directory**

```bash
rm -rf src/main/java/com/wpanther/notification/infrastructure/persistence/
```

**Step 5: Update test file for persistence**

In `NotificationRepositoryImplTest.java`:
- Move to: `src/test/java/com/wpanther/notification/adapter/out/persistence/NotificationRepositoryAdapterTest.java`
- Update package to `com.wpanther.notification.adapter.out.persistence`
- Rename class to `NotificationRepositoryAdapterTest`
- Update all `NotificationRepositoryImpl` references → `NotificationRepositoryAdapter`
- Update import: `import com.wpanther.notification.adapter.out.persistence.NotificationRepositoryAdapter;`

Similarly move `JpaOutboxEventRepositoryTest.java` and `OutboxEventEntityTest.java` to the new test path and update their packages.

**Step 6: Verify compilation and run tests**

```bash
mvn test
```
Expected: BUILD SUCCESS.

---

### Task 15: Move messaging layer → adapter/in/kafka

Move all files from `infrastructure/messaging/` to `adapter/in/kafka/`. Route file name stays the same.

**Files moved:**
- `infrastructure/messaging/NotificationEventRoutes.java` → `adapter/in/kafka/NotificationEventRoutes.java`
- `infrastructure/messaging/InvoiceProcessedEvent.java` → `adapter/in/kafka/InvoiceProcessedEvent.java`
- `infrastructure/messaging/TaxInvoiceProcessedEvent.java` → `adapter/in/kafka/TaxInvoiceProcessedEvent.java`
- `infrastructure/messaging/InvoicePdfGeneratedEvent.java` → `adapter/in/kafka/InvoicePdfGeneratedEvent.java`
- `infrastructure/messaging/PdfSignedEvent.java` → `adapter/in/kafka/PdfSignedEvent.java`
- `infrastructure/messaging/XmlSignedEvent.java` → `adapter/in/kafka/XmlSignedEvent.java`
- `infrastructure/messaging/EbmsSentEvent.java` → `adapter/in/kafka/EbmsSentEvent.java`
- `infrastructure/messaging/DocumentReceivedEvent.java` → `adapter/in/kafka/DocumentReceivedEvent.java`
- `infrastructure/messaging/DocumentReceivedCountingEvent.java` → `adapter/in/kafka/DocumentReceivedCountingEvent.java`
- `infrastructure/messaging/saga/SagaStartedEvent.java` → `adapter/in/kafka/saga/SagaStartedEvent.java`
- `infrastructure/messaging/saga/SagaStepCompletedEvent.java` → `adapter/in/kafka/saga/SagaStepCompletedEvent.java`
- `infrastructure/messaging/saga/SagaCompletedEvent.java` → `adapter/in/kafka/saga/SagaCompletedEvent.java`
- `infrastructure/messaging/saga/SagaFailedEvent.java` → `adapter/in/kafka/saga/SagaFailedEvent.java`

**Step 1: Create directories**

```bash
mkdir -p src/main/java/com/wpanther/notification/adapter/in/kafka/saga
```

**Step 2: Update all package declarations**

For all event DTOs and the routes file:
- Old: `package com.wpanther.notification.infrastructure.messaging;`
- New: `package com.wpanther.notification.adapter.in.kafka;`

For saga event DTOs:
- Old: `package com.wpanther.notification.infrastructure.messaging.saga;`
- New: `package com.wpanther.notification.adapter.in.kafka.saga;`

**Step 3: Update all imports that reference old messaging package**

Files that currently import from `infrastructure/messaging/`:
- `application/port/in/ProcessingEventUseCase.java` — update 6 event DTO imports
- `application/port/in/DocumentReceivedEventUseCase.java` — update 2 event DTO imports
- `application/port/in/SagaEventUseCase.java` — update 4 saga event DTO imports
- `application/service/NotificationService.java` — update all 13 event DTO imports
- All test files that import event DTOs

Run this search to find all affected files:
```bash
grep -r "infrastructure.messaging" src/ --include="*.java" -l
```

For each file found, change:
```java
// FROM:
import com.wpanther.notification.infrastructure.messaging.InvoiceProcessedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaCompletedEvent;
// etc.

// TO:
import com.wpanther.notification.adapter.in.kafka.InvoiceProcessedEvent;
import com.wpanther.notification.adapter.in.kafka.saga.SagaCompletedEvent;
// etc.
```

**Step 4: Delete original infrastructure/messaging directory**

```bash
rm -rf src/main/java/com/wpanther/notification/infrastructure/messaging/
```

**Step 5: Move and update test files for messaging**

Move test files:
- `infrastructure/messaging/NotificationEventRoutesTest.java` → `adapter/in/kafka/NotificationEventRoutesTest.java`
- `infrastructure/messaging/XmlSignedEventTest.java` → `adapter/in/kafka/XmlSignedEventTest.java`
- `infrastructure/messaging/saga/SagaStartedEventTest.java` → `adapter/in/kafka/saga/SagaStartedEventTest.java`
- (and the other 3 saga event test files)

For each test file: update the `package` declaration and any imports.

**Step 6: Verify compilation and run tests**

```bash
mvn test
```
Expected: BUILD SUCCESS.

---

### Task 16: Move notification senders → adapter/out/notification and rename

Move `infrastructure/notification/` → `adapter/out/notification/`. Rename the sender classes.

**Files:**
- `infrastructure/notification/EmailNotificationSender.java` → `adapter/out/notification/EmailNotificationSenderAdapter.java`
- `infrastructure/notification/WebhookNotificationSender.java` → `adapter/out/notification/WebhookNotificationSenderAdapter.java`
- `infrastructure/notification/TemplateEngine.java` → `adapter/out/notification/TemplateEngine.java`

**Step 1: Create directory**

```bash
mkdir -p src/main/java/com/wpanther/notification/adapter/out/notification
```

**Step 2: Move and update each file**

For `EmailNotificationSenderAdapter.java` (renamed from `EmailNotificationSender`):
- New package: `package com.wpanther.notification.adapter.out.notification;`
- Class name: `EmailNotificationSenderAdapter`

For `WebhookNotificationSenderAdapter.java`:
- New package: `package com.wpanther.notification.adapter.out.notification;`
- Class name: `WebhookNotificationSenderAdapter`

For `TemplateEngine.java`:
- New package: `package com.wpanther.notification.adapter.out.notification;`
- Class name: `TemplateEngine` (unchanged)

**Step 3: Check if any files reference the old class names**

```bash
grep -r "EmailNotificationSender\b" src/ --include="*.java" -l
grep -r "WebhookNotificationSender\b" src/ --include="*.java" -l
```

Update any found references. The senders are typically auto-wired as `List<NotificationSenderPort>` by Spring, so direct class name references should be minimal (mainly in tests and `@MockBean` annotations).

**Step 4: Delete infrastructure/notification directory**

```bash
rm -rf src/main/java/com/wpanther/notification/infrastructure/notification/
```

**Step 5: Move and update test files**

- `infrastructure/notification/EmailNotificationSenderTest.java` → `adapter/out/notification/EmailNotificationSenderAdapterTest.java`
- `infrastructure/notification/WebhookNotificationSenderTest.java` → `adapter/out/notification/WebhookNotificationSenderAdapterTest.java`
- `infrastructure/notification/TemplateEngineTest.java` → `adapter/out/notification/TemplateEngineTest.java`

Update each: package declaration, class name (for the renamed senders), and `import` of the adapter class.

**Step 6: Verify compilation and run tests**

```bash
mvn test
```
Expected: BUILD SUCCESS.

---

### Task 17: Move REST controller → adapter/in/rest

**Files:**
- `application/controller/NotificationController.java` → `adapter/in/rest/NotificationController.java`
- `application/dto/ErrorResponse.java` → `adapter/in/rest/ErrorResponse.java` (move with controller)
- `infrastructure/config/GlobalExceptionHandler.java` → stays in `infrastructure/config/` (it's a Spring cross-cutting concern, not REST-specific)

**Step 1: Create directory**

```bash
mkdir -p src/main/java/com/wpanther/notification/adapter/in/rest
```

**Step 2: Move and update NotificationController.java**

- New package: `package com.wpanther.notification.adapter.in.rest;`
- Update imports if `ErrorResponse` is imported from the old `application/dto/` location

**Step 3: Move and update ErrorResponse.java (if it exists)**

```bash
# Check if ErrorResponse is in application/dto/
ls src/main/java/com/wpanther/notification/application/dto/
```

If it exists, move it to `adapter/in/rest/` and update package.

**Step 4: Delete old directories**

```bash
rm -rf src/main/java/com/wpanther/notification/application/controller/
# Only remove dto/ if you moved ErrorResponse:
rm -rf src/main/java/com/wpanther/notification/application/dto/
```

**Step 5: Move and update test file**

- `application/controller/NotificationControllerTest.java` → `adapter/in/rest/NotificationControllerTest.java`

Update package declaration and imports in the test file.

**Step 6: Verify compilation and run tests**

```bash
mvn test
```
Expected: BUILD SUCCESS, 147 tests passing.

---

### Task 18: Final verification and commit Phase 2

**Step 1: Confirm no references remain to old infrastructure packages**

```bash
grep -r "infrastructure\.messaging" src/ --include="*.java"
grep -r "infrastructure\.persistence" src/ --include="*.java"
grep -r "infrastructure\.notification" src/ --include="*.java"
grep -r "application\.controller" src/ --include="*.java"
```
All commands should return no output.

**Step 2: Confirm no direct references to concrete service from adapters**

```bash
grep -r "NotificationService" src/main/java/com/wpanther/notification/adapter/ --include="*.java"
```
Should return no output (adapters use port interfaces only).

**Step 3: Run full test suite with coverage check**

```bash
mvn verify
```
Expected: BUILD SUCCESS, 147 tests passing, JaCoCo 90% coverage requirement met.

**Step 4: Commit Phase 2**

```bash
git add -A
git commit -m "Rename packages to hexagonal adapter/in|out structure (Phase 2)"
```

---

## Final Package Verification

After both phases, confirm the structure matches the design:

```bash
find src/main/java/com/wpanther/notification -type d | sort
```

Expected output:
```
.../notification
.../notification/adapter
.../notification/adapter/in
.../notification/adapter/in/kafka
.../notification/adapter/in/kafka/saga
.../notification/adapter/in/rest
.../notification/adapter/out
.../notification/adapter/out/notification
.../notification/adapter/out/persistence
.../notification/adapter/out/persistence/outbox
.../notification/application
.../notification/application/port
.../notification/application/port/in
.../notification/application/port/out
.../notification/application/service
.../notification/domain
.../notification/domain/exception
.../notification/domain/model
.../notification/infrastructure
.../notification/infrastructure/config
```

And confirm the dependency rule — adapters never reference concrete service:
```bash
grep -r "NotificationService\|NotificationSendingService\|NotificationDispatcherService" \
  src/main/java/com/wpanther/notification/adapter/ --include="*.java"
```
Expected: no output.
