# Rename PdfGeneratedEvent → InvoicePdfGeneratedEvent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename `PdfGeneratedEvent` to `InvoicePdfGeneratedEvent` across all Java sources and documentation for naming consistency with `TaxInvoicePdfGeneratedEvent`.

**Architecture:** Pure symbol rename — delete the old class file, create the new one, update all 5 Java source references (import + method names), then update 6 documentation files. No logic, schema, topic, or template changes.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, Maven, JUnit 5

---

### Task 1: Create InvoicePdfGeneratedEvent.java (rename the class)

**Files:**
- Create: `src/main/java/com/wpanther/notification/application/port/in/event/InvoicePdfGeneratedEvent.java`
- Delete: `src/main/java/com/wpanther/notification/application/port/in/event/PdfGeneratedEvent.java`

- [ ] **Step 1: Create the renamed class file**

Create `src/main/java/com/wpanther/notification/application/port/in/event/InvoicePdfGeneratedEvent.java` with this exact content:

```java
package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when invoice PDF generation is completed
 */
@Getter
public class InvoicePdfGeneratedEvent extends TraceEvent {

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentUrl")
    private final String documentUrl;

    @JsonProperty("fileSize")
    private final long fileSize;

    @JsonProperty("xmlEmbedded")
    private final boolean xmlEmbedded;

    @JsonProperty("digitallySigned")
    private final boolean digitallySigned;

    /**
     * Constructor for creating new events.
     * Generates eventId, occurredAt, eventType, and version automatically.
     */
    public InvoicePdfGeneratedEvent(String invoiceId, String invoiceNumber, String documentId,
                                    String documentUrl, long fileSize, boolean xmlEmbedded,
                                    boolean digitallySigned, String correlationId) {
        super(invoiceId, correlationId, "pdf-generation-service", "PDF_GENERATED", null);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentId = documentId;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
        this.digitallySigned = digitallySigned;
    }

    /**
     * Constructor for deserialization from JSON.
     * Used by Jackson when reading events from Kafka.
     */
    @JsonCreator
    public InvoicePdfGeneratedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("documentUrl") String documentUrl,
        @JsonProperty("fileSize") long fileSize,
        @JsonProperty("xmlEmbedded") boolean xmlEmbedded,
        @JsonProperty("digitallySigned") boolean digitallySigned
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentId = documentId;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
        this.digitallySigned = digitallySigned;
    }
}
```

- [ ] **Step 2: Delete the old class file**

```bash
rm src/main/java/com/wpanther/notification/application/port/in/event/PdfGeneratedEvent.java
```

---

### Task 2: Update ProcessingEventUseCase.java

**Files:**
- Modify: `src/main/java/com/wpanther/notification/application/usecase/ProcessingEventUseCase.java`

- [ ] **Step 1: Update import and method signature**

Replace in `src/main/java/com/wpanther/notification/application/usecase/ProcessingEventUseCase.java`:

Old:
```java
import com.wpanther.notification.application.port.in.event.PdfGeneratedEvent;
```
New:
```java
import com.wpanther.notification.application.port.in.event.InvoicePdfGeneratedEvent;
```

Old:
```java
    void handlePdfGenerated(PdfGeneratedEvent event);
```
New:
```java
    void handleInvoicePdfGenerated(InvoicePdfGeneratedEvent event);
```

---

### Task 3: Update NotificationService.java

**Files:**
- Modify: `src/main/java/com/wpanther/notification/application/service/NotificationService.java`

- [ ] **Step 1: Update import, method signature, and log message**

Replace in `src/main/java/com/wpanther/notification/application/service/NotificationService.java`:

Old:
```java
import com.wpanther.notification.application.port.in.event.PdfGeneratedEvent;
```
New:
```java
import com.wpanther.notification.application.port.in.event.InvoicePdfGeneratedEvent;
```

Old:
```java
    @Override
    public void handlePdfGenerated(PdfGeneratedEvent event) {
        log.info("Processing PdfGeneratedEvent: invoiceId={}, invoiceNumber={}",
```
New:
```java
    @Override
    public void handleInvoicePdfGenerated(InvoicePdfGeneratedEvent event) {
        log.info("Processing InvoicePdfGeneratedEvent: invoiceId={}, invoiceNumber={}",
```

---

### Task 4: Update NotificationEventRoutes.java

**Files:**
- Modify: `src/main/java/com/wpanther/notification/infrastructure/adapter/in/kafka/NotificationEventRoutes.java`

- [ ] **Step 1: Update import**

Replace:
```java
import com.wpanther.notification.application.port.in.event.PdfGeneratedEvent;
```
With:
```java
import com.wpanther.notification.application.port.in.event.InvoicePdfGeneratedEvent;
```

- [ ] **Step 2: Update Route 3 log and unmarshal**

Replace:
```java
            .log("Received PdfGeneratedEvent from Kafka")
```
With:
```java
            .log("Received InvoicePdfGeneratedEvent from Kafka")
```

Replace:
```java
            .unmarshal().json(JsonLibrary.Jackson, PdfGeneratedEvent.class)
            .process(this::handlePdfGenerated)
```
With:
```java
            .unmarshal().json(JsonLibrary.Jackson, InvoicePdfGeneratedEvent.class)
            .process(this::handleInvoicePdfGenerated)
```

- [ ] **Step 3: Update private handler method**

Replace:
```java
    private void handlePdfGenerated(Exchange exchange) {
        PdfGeneratedEvent event = exchange.getIn().getBody(PdfGeneratedEvent.class);
        processingEventUseCase.handlePdfGenerated(event);
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }
```
With:
```java
    private void handleInvoicePdfGenerated(Exchange exchange) {
        InvoicePdfGeneratedEvent event = exchange.getIn().getBody(InvoicePdfGeneratedEvent.class);
        processingEventUseCase.handleInvoicePdfGenerated(event);
        exchange.getIn().setHeader("invoiceNumber", event.getInvoiceNumber());
    }
```

---

### Task 5: Update NotificationServiceTest.java

**Files:**
- Modify: `src/test/java/com/wpanther/notification/application/service/NotificationServiceTest.java`

- [ ] **Step 1: Update import**

Replace:
```java
import com.wpanther.notification.application.port.in.event.PdfGeneratedEvent;
```
With:
```java
import com.wpanther.notification.application.port.in.event.InvoicePdfGeneratedEvent;
```

- [ ] **Step 2: Update test method**

Replace:
```java
    // ── ProcessingEventUseCase: handlePdfGenerated (pdf.generated.invoice) ───────────────

    @Test
    @DisplayName("handlePdfGenerated creates PDF_GENERATED notification and dispatches async")
    void testHandlePdfGenerated_dispatchesAsync() {
        PdfGeneratedEvent event = new PdfGeneratedEvent(
            UUID.randomUUID(), Instant.now(), "pdf.generated.invoice", 1,
            "saga-1", "corr-1", "invoice-pdf-generation-service", "PDF_GENERATED", null,
            "INV-001", "INV-2025-001", "doc-001", "http://example.com/doc", 102400L, true, false);

        ReflectionTestUtils.setField(notificationService, "defaultRecipient", "admin@example.com");

        notificationService.handlePdfGenerated(event);
```
With:
```java
    // ── ProcessingEventUseCase: handleInvoicePdfGenerated (pdf.generated.invoice) ─────────

    @Test
    @DisplayName("handleInvoicePdfGenerated creates PDF_GENERATED notification and dispatches async")
    void testHandleInvoicePdfGenerated_dispatchesAsync() {
        InvoicePdfGeneratedEvent event = new InvoicePdfGeneratedEvent(
            UUID.randomUUID(), Instant.now(), "pdf.generated.invoice", 1,
            "saga-1", "corr-1", "invoice-pdf-generation-service", "PDF_GENERATED", null,
            "INV-001", "INV-2025-001", "doc-001", "http://example.com/doc", 102400L, true, false);

        ReflectionTestUtils.setField(notificationService, "defaultRecipient", "admin@example.com");

        notificationService.handleInvoicePdfGenerated(event);
```

---

### Task 6: Update KafkaConsumerIntegrationTest.java

**Files:**
- Modify: `src/test/java/com/wpanther/notification/integration/KafkaConsumerIntegrationTest.java`

- [ ] **Step 1: Update import**

Replace:
```java
import com.wpanther.notification.application.port.in.event.PdfGeneratedEvent;
```
With:
```java
import com.wpanther.notification.application.port.in.event.InvoicePdfGeneratedEvent;
```

- [ ] **Step 2: Update test method name, display name, instantiation, and topic**

Replace:
```java
    @Test
    @DisplayName("Should consume PdfGeneratedEvent and create notification")
    void shouldConsumePdfGeneratedEvent() {
        // Given
        String invoiceId = "INV-" + UUID.randomUUID();
        String invoiceNumber = "T0001-" + System.currentTimeMillis();
        String documentId = "DOC-" + UUID.randomUUID();
        String documentUrl = "http://localhost:8084/api/v1/documents/" + documentId;
        String correlationId = UUID.randomUUID().toString();
        long fileSize = 125000; // 125 KB

        PdfGeneratedEvent event = new PdfGeneratedEvent(
            invoiceId, invoiceNumber, documentId, documentUrl, fileSize,
            true,  // xmlEmbedded
            false, // digitallySigned
            correlationId
        );

        // When
        sendEvent("pdf.generated", invoiceId, event);
```
With:
```java
    @Test
    @DisplayName("Should consume InvoicePdfGeneratedEvent and create notification")
    void shouldConsumeInvoicePdfGeneratedEvent() {
        // Given
        String invoiceId = "INV-" + UUID.randomUUID();
        String invoiceNumber = "T0001-" + System.currentTimeMillis();
        String documentId = "DOC-" + UUID.randomUUID();
        String documentUrl = "http://localhost:8084/api/v1/documents/" + documentId;
        String correlationId = UUID.randomUUID().toString();
        long fileSize = 125000; // 125 KB

        InvoicePdfGeneratedEvent event = new InvoicePdfGeneratedEvent(
            invoiceId, invoiceNumber, documentId, documentUrl, fileSize,
            true,  // xmlEmbedded
            false, // digitallySigned
            correlationId
        );

        // When
        sendEvent("pdf.generated.invoice", invoiceId, event);
```

> **Note:** The topic was also corrected from `"pdf.generated"` (phantom) to `"pdf.generated.invoice"` (actual topic consumed by the route) to fix a pre-existing bug in the integration test.

---

### Task 7: Verify compilation and unit tests pass

- [ ] **Step 1: Compile**

```bash
mvn compile -q
```
Expected: `BUILD SUCCESS` with no errors.

- [ ] **Step 2: Run unit tests**

```bash
mvn test
```
Expected: `BUILD SUCCESS`, 147 tests pass, 0 failures.

- [ ] **Step 3: Commit all Java source changes**

```bash
git add \
  src/main/java/com/wpanther/notification/application/port/in/event/InvoicePdfGeneratedEvent.java \
  src/main/java/com/wpanther/notification/application/usecase/ProcessingEventUseCase.java \
  src/main/java/com/wpanther/notification/application/service/NotificationService.java \
  src/main/java/com/wpanther/notification/infrastructure/adapter/in/kafka/NotificationEventRoutes.java \
  src/test/java/com/wpanther/notification/application/service/NotificationServiceTest.java \
  src/test/java/com/wpanther/notification/integration/KafkaConsumerIntegrationTest.java
git rm src/main/java/com/wpanther/notification/application/port/in/event/PdfGeneratedEvent.java
git commit -m "refactor: rename PdfGeneratedEvent to InvoicePdfGeneratedEvent"
```

---

### Task 8: Update README.md

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update Consumed Topics table**

Replace:
```
| `pdf.generated.invoice` | PdfGeneratedEvent | notification-pdf-generated | Email notification |
```
With:
```
| `pdf.generated.invoice` | InvoicePdfGeneratedEvent | notification-pdf-generated | Email notification |
```

- [ ] **Step 2: Update Event Schemas heading and eventType value**

Replace:
```
**PdfGeneratedEvent** (extends IntegrationEvent, topic: `pdf.generated.invoice`)
```
With:
```
**InvoicePdfGeneratedEvent** (extends TraceEvent, topic: `pdf.generated.invoice`)
```

Replace inside the JSON block:
```json
  "eventType": "PdfGeneratedEvent",
```
With:
```json
  "eventType": "InvoicePdfGeneratedEvent",
```

- [ ] **Step 3: Update integration test name**

Replace:
```
- `shouldConsumePdfGeneratedEvent()` - Verifies `pdf.generated.invoice` topic consumption and notification creation
```
With:
```
- `shouldConsumeInvoicePdfGeneratedEvent()` - Verifies `pdf.generated.invoice` topic consumption and notification creation
```

---

### Task 9: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Update route table row**

Replace:
```
| `notification-pdf-generated` | `pdf.generated.invoice` | `PdfGeneratedEvent` | Creates EMAIL notification with `pdf-generated` template |
```
With:
```
| `notification-pdf-generated` | `pdf.generated.invoice` | `InvoicePdfGeneratedEvent` | Creates EMAIL notification with `pdf-generated` template |
```

- [ ] **Step 2: Update processing events list**

Replace:
```
3. `PdfGeneratedEvent` - Invoice PDF generation completed (topic: `pdf.generated.invoice`)
```
With:
```
3. `InvoicePdfGeneratedEvent` - Invoice PDF generation completed (topic: `pdf.generated.invoice`)
```

- [ ] **Step 3: Update integration test list**

Replace:
```
  - `shouldConsumePdfGeneratedEvent()` - Tests `pdf.generated.invoice` topic consumption
```
With:
```
  - `shouldConsumeInvoicePdfGeneratedEvent()` - Tests `pdf.generated.invoice` topic consumption
```

---

### Task 10: Update PROGRAM_FLOW.md

**Files:**
- Modify: `docs/PROGRAM_FLOW.md`

- [ ] **Step 1: Replace all occurrences**

In `docs/PROGRAM_FLOW.md`, replace every occurrence of `PdfGeneratedEvent` with `InvoicePdfGeneratedEvent` and every occurrence of `handlePdfGenerated` with `handleInvoicePdfGenerated` and every occurrence of `shouldConsumePdfGeneratedEvent` with `shouldConsumeInvoicePdfGeneratedEvent`.

Verify with:
```bash
grep -n "PdfGeneratedEvent\|handlePdfGenerated\|shouldConsumePdfGenerated" docs/PROGRAM_FLOW.md
```
Expected: no output (all occurrences replaced).

---

### Task 11: Update IMPLEMENTATION_SUMMARY.md

**Files:**
- Modify: `IMPLEMENTATION_SUMMARY.md`

- [ ] **Step 1: Replace all occurrences**

In `IMPLEMENTATION_SUMMARY.md`, replace every occurrence of `PdfGeneratedEvent` with `InvoicePdfGeneratedEvent` and every occurrence of `handlePdfGenerated` with `handleInvoicePdfGenerated`.

Verify with:
```bash
grep -n "PdfGeneratedEvent\|handlePdfGenerated" IMPLEMENTATION_SUMMARY.md
```
Expected: no output (all occurrences replaced).

---

### Task 12: Update archived plan docs

**Files:**
- Modify: `docs/plans/2026-03-09-hexagonal-architecture-migration-design.md`
- Modify: `docs/plans/2026-03-03-hexagonal-architecture-migration.md`

- [ ] **Step 1: Update 2026-03-09 design doc**

In `docs/plans/2026-03-09-hexagonal-architecture-migration-design.md`, replace every occurrence of `PdfGeneratedEvent` with `InvoicePdfGeneratedEvent`.

Verify:
```bash
grep -n "PdfGeneratedEvent" docs/plans/2026-03-09-hexagonal-architecture-migration-design.md
```
Expected: no output.

- [ ] **Step 2: Update 2026-03-03 migration plan**

In `docs/plans/2026-03-03-hexagonal-architecture-migration.md`, replace every occurrence of `PdfGeneratedEvent` with `InvoicePdfGeneratedEvent` and every occurrence of `handlePdfGenerated` with `handleInvoicePdfGenerated`.

Verify:
```bash
grep -n "PdfGeneratedEvent\|handlePdfGenerated" docs/plans/2026-03-03-hexagonal-architecture-migration.md
```
Expected: no output.

---

### Task 13: Commit documentation changes

- [ ] **Step 1: Commit all doc changes**

```bash
git add README.md CLAUDE.md IMPLEMENTATION_SUMMARY.md docs/
git commit -m "docs: update all references from PdfGeneratedEvent to InvoicePdfGeneratedEvent"
```

- [ ] **Step 2: Push**

```bash
git push
```
