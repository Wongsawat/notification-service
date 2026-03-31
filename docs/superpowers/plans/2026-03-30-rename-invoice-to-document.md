# Rename invoiceId/invoiceNumber to documentId/documentNumber - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename all `invoiceId`/`invoiceNumber` references to `documentId`/`documentNumber` across the entire notification-service.

**Architecture:** Single atomic refactor across all layers — domain model, event DTOs, repository, application service, infrastructure adapters, templates, database migration, and tests. All producing services have already switched to `documentId`/`documentNumber`.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, Lombok, Thymeleaf, PostgreSQL, Flyway, JUnit 5

---

### Task 1: Consolidate Database Migration

**Files:**
- Modify: `src/main/resources/db/migration/V1__create_notifications_table.sql` (rewrite)
- Delete: `src/main/resources/db/migration/V2__create_outbox_events_table.sql`
- Delete: `src/main/resources/db/migration/V3__add_status_createdat_composite_index.sql`
- Delete: `src/main/resources/db/migration/V4__alter_timestamp_columns_to_timestamptz.sql`

- [ ] **Step 1: Rewrite V1 migration with consolidated schema**

Replace `V1__create_notifications_table.sql` with this content. Key changes: `invoice_id` → `document_id`, `invoice_number` → `document_number`, `TIMESTAMPTZ` from the start, composite index included:

```sql
-- Consolidated migration: notifications + outbox tables
-- Fresh installation only (V2-V4 deleted)

-- Create notifications table
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    recipient VARCHAR(500) NOT NULL,
    subject VARCHAR(500),
    body TEXT,
    metadata TEXT,
    template_name VARCHAR(100),
    template_variables TEXT,
    document_id VARCHAR(100),
    document_number VARCHAR(100),
    correlation_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

-- Create indexes for common queries
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_document_id ON notifications(document_id);
CREATE INDEX idx_notifications_document_number ON notifications(document_number);
CREATE INDEX idx_notifications_recipient ON notifications(recipient);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_channel ON notifications(channel);
CREATE INDEX idx_notifications_status_created_at ON notifications(status, created_at DESC);

-- Create index for failed notifications retry query
CREATE INDEX idx_notifications_failed_retry ON notifications(status, retry_count) WHERE status = 'FAILED';

-- Comments
COMMENT ON TABLE notifications IS 'Notification records for email, SMS, and webhook notifications';
COMMENT ON COLUMN notifications.metadata IS 'Additional metadata as JSON text';
COMMENT ON COLUMN notifications.template_variables IS 'Template variable values as JSON text';
COMMENT ON COLUMN notifications.retry_count IS 'Number of retry attempts';

-- Outbox pattern table for reliable event publishing via Debezium CDC
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    topic VARCHAR(255),
    partition_key VARCHAR(255),
    headers TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);

-- Outbox indexes
CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created ON outbox_events(created_at);
CREATE INDEX idx_outbox_debezium ON outbox_events(created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);

COMMENT ON TABLE outbox_events IS 'Transactional outbox for reliable event publishing via Debezium CDC';
```

- [ ] **Step 2: Delete V2, V3, V4 migration files**

Run: `rm src/main/resources/db/migration/V2__create_outbox_events_table.sql src/main/resources/db/migration/V3__add_status_createdat_composite_index.sql src/main/resources/db/migration/V4__alter_timestamp_columns_to_timestamptz.sql`

---

### Task 2: Rename Domain Model + Repository Layer

**Files:**
- Modify: `src/main/java/com/wpanther/notification/domain/model/Notification.java`
- Modify: `src/main/java/com/wpanther/notification/domain/repository/NotificationRepository.java`
- Modify: `src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/NotificationEntity.java`
- Modify: `src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/JpaNotificationRepository.java`
- Modify: `src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/NotificationRepositoryAdapter.java`
- Modify: `src/main/java/com/wpanther/notification/application/usecase/QueryNotificationUseCase.java`

- [ ] **Step 1: Rename in `Notification.java`**

Apply these replacements using `replace_all`:
- `invoiceId` → `documentId` (all occurrences)
- `invoiceNumber` → `documentNumber` (all occurrences)
- `getInvoiceId()` → `getDocumentId()`
- `setInvoiceId(` → `setDocumentId(`
- `getInvoiceNumber()` → `getDocumentNumber()`
- `setInvoiceNumber(` → `setDocumentNumber(`
- `.invoiceId(` → `.documentId(` (builder method)
- `.invoiceNumber(` → `.documentNumber(` (builder method)

Also update Javadoc:
- `({@code subject}, {@code invoiceId},\n     * {@code invoiceNumber}, {@code correlationId})` → `({@code subject}, {@code documentId},\n     * {@code documentNumber}, {@code correlationId})`
- `Sets the associated invoice ID` → `Sets the associated document ID`
- `Sets the human-readable invoice number` → `Sets the human-readable document number`

- [ ] **Step 2: Rename in `NotificationRepository.java`**

Apply replacements:
- `findByInvoiceId` → `findByDocumentId` (2 occurrences: parameter + method name)
- `findByInvoiceNumber` → `findByDocumentNumber` (2 occurrences)

- [ ] **Step 3: Rename in `NotificationEntity.java`**

Apply replacements:
- `@Index(name = "idx_invoice_id", columnList = "invoice_id")` → `@Index(name = "idx_document_id", columnList = "document_id")`
- `@Column(name = "invoice_id"` → `@Column(name = "document_id"`
- `private String invoiceId;` → `private String documentId;`
- `private String invoiceNumber;` → `private String documentNumber;`

- [ ] **Step 4: Rename in `JpaNotificationRepository.java`**

Apply replacements:
- `findByInvoiceId` → `findByDocumentId` (2 occurrences)
- `findByInvoiceNumber` → `findByDocumentNumber` (2 occurrences)

- [ ] **Step 5: Rename in `NotificationRepositoryAdapter.java`**

Apply replacements:
- `findByInvoiceId` → `findByDocumentId` (2 occurrences)
- `findByInvoiceNumber` → `findByDocumentNumber` (2 occurrences)
- `.invoiceId(` → `.documentId(` (2 occurrences: toEntity + toDomain)
- `.invoiceNumber(` → `.documentNumber(` (2 occurrences)
- `getInvoiceId()` → `getDocumentId()` (1 occurrence, in findByDocumentId method delegation)
- `getInvoiceNumber()` → `getDocumentNumber()` (1 occurrence)

- [ ] **Step 6: Rename in `QueryNotificationUseCase.java`**

Apply replacement:
- `findByInvoiceId` → `findByDocumentId` (2 occurrences)

---

### Task 3: Rename All Event DTOs

**Files:**
- Modify: `src/main/java/com/wpanther/notification/application/port/in/event/InvoiceProcessedEvent.java`
- Modify: `src/main/java/com/wpanther/notification/application/port/in/event/TaxInvoiceProcessedEvent.java`
- Modify: `src/main/java/com/wpanther/notification/application/port/in/event/InvoicePdfGeneratedEvent.java`
- Modify: `src/main/java/com/wpanther/notification/application/port/in/event/TaxInvoicePdfGeneratedEvent.java`
- Modify: `src/main/java/com/wpanther/notification/application/port/in/event/PdfSignedEvent.java`
- Modify: `src/main/java/com/wpanther/notification/application/port/in/event/XmlSignedEvent.java`
- Modify: `src/main/java/com/wpanther/notification/application/port/in/event/EbmsSentEvent.java`
- Modify: `src/main/java/com/wpanther/notification/application/port/in/event/DocumentReceivedEvent.java`
- Modify: `src/main/java/com/wpanther/notification/application/port/in/event/saga/SagaStartedEvent.java`
- Modify: `src/main/java/com/wpanther/notification/application/port/in/event/saga/SagaStepCompletedEvent.java`
- Modify: `src/main/java/com/wpanther/notification/application/port/in/event/saga/SagaCompletedEvent.java`
- Modify: `src/main/java/com/wpanther/notification/application/port/in/event/saga/SagaFailedEvent.java`

- [ ] **Step 1: Rename in simple event DTOs** (InvoiceProcessedEvent, TaxInvoiceProcessedEvent, PdfSignedEvent, XmlSignedEvent)

For each of these 4 files, apply `replace_all`:
- `invoiceId` → `documentId`
- `invoiceNumber` → `documentNumber`

These events have identical field structure — just rename the fields, `@JsonProperty`, constructor params, and assignments.

- [ ] **Step 2: Rename in `InvoicePdfGeneratedEvent.java` — remove `invoiceId`, rename `invoiceNumber`**

This event has BOTH `invoiceId` and `documentId`. Remove `invoiceId` field entirely. Apply:
- `@JsonProperty("invoiceId")\n    private final String invoiceId;\n\n    ` → `` (delete the field declaration)
- `@JsonProperty("invoiceNumber")` → `@JsonProperty("documentNumber")`
- `private final String invoiceNumber;` → `private final String documentNumber;`
- In the creation constructor: remove the `invoiceId` parameter and `this.invoiceId = invoiceId;` assignment
- In the `@JsonCreator`: remove the `@JsonProperty("invoiceId") String invoiceId,` parameter and `this.invoiceId = invoiceId;` assignment
- `this.invoiceNumber = invoiceNumber;` → `this.documentNumber = documentNumber;`
- All remaining `invoiceNumber` → `documentNumber`

- [ ] **Step 3: Rename in `TaxInvoicePdfGeneratedEvent.java` — remove `taxInvoiceId`, rename `taxInvoiceNumber`**

This event has `documentId`, `taxInvoiceId`, `taxInvoiceNumber`. Remove `taxInvoiceId` (duplicate of `documentId`), rename `taxInvoiceNumber` → `documentNumber`:
- `@JsonProperty("taxInvoiceId")\n    private final String taxInvoiceId;\n\n    ` → `` (delete)
- `@JsonProperty("taxInvoiceNumber")` → `@JsonProperty("documentNumber")`
- `private final String taxInvoiceNumber;` → `private final String documentNumber;`
- In creation constructor: remove `String taxInvoiceId,` parameter, `this.taxInvoiceId = taxInvoiceId;` assignment
- `this.taxInvoiceNumber = taxInvoiceNumber;` → `this.documentNumber = documentNumber;`
- In `@JsonCreator`: remove `@JsonProperty("taxInvoiceId") String taxInvoiceId,` parameter and `this.taxInvoiceId = taxInvoiceId;` assignment
- `this.taxInvoiceNumber = taxInvoiceNumber;` → `this.documentNumber = documentNumber;`

- [ ] **Step 4: Rename in `EbmsSentEvent.java` — remove `invoiceId`, rename `invoiceNumber`**

This event has BOTH `invoiceId` and `documentId`. Remove `invoiceId`:
- `@JsonProperty("invoiceId")\n    private final String invoiceId;\n\n    ` → `` (delete)
- `@JsonProperty("invoiceNumber")` → `@JsonProperty("documentNumber")`
- `private final String invoiceNumber;` → `private final String documentNumber;`
- In creation constructor: remove `String invoiceId,` parameter, `this.invoiceId = invoiceId;` assignment
- `this.invoiceNumber = invoiceNumber;` → `this.documentNumber = documentNumber;`
- In `@JsonCreator`: remove `@JsonProperty("invoiceId") String invoiceId,` parameter and `this.invoiceId = invoiceId;` assignment
- `this.invoiceNumber = invoiceNumber;` → `this.documentNumber = documentNumber;`

- [ ] **Step 5: Rename in `DocumentReceivedEvent.java`**

Only has `invoiceNumber` (no `invoiceId`):
- `invoiceNumber` → `documentNumber` (all occurrences)

- [ ] **Step 6: Rename in `SagaStartedEvent.java` + remove stale `startedAt`**

- `invoiceNumber` → `documentNumber` (all occurrences)
- Remove `startedAt` field and all references:
  - Delete: `@JsonProperty("startedAt")\n    private final Instant startedAt;`
  - In creation constructor: remove `Instant startedAt` parameter, `this.startedAt = startedAt;` assignment
  - In `@JsonCreator`: remove `@JsonProperty("startedAt") Instant startedAt` parameter, `this.startedAt = startedAt;` assignment

- [ ] **Step 7: Clean up `SagaStepCompletedEvent.java` — remove stale fields**

Remove `documentId`, `invoiceNumber`, `stepDurationMs` fields and all references:
- Delete the 3 field declarations: `documentId`, `invoiceNumber`, `stepDurationMs`
- In creation constructor: remove `String documentId,` and `String invoiceNumber, Long stepDurationMs` parameters and their assignments
- In `@JsonCreator`: remove the 3 corresponding `@JsonProperty` parameters and assignments

- [ ] **Step 8: Rename in `SagaCompletedEvent.java`**

- `invoiceNumber` → `documentNumber` (all occurrences)

- [ ] **Step 9: Rename in `SagaFailedEvent.java`**

- `invoiceNumber` → `documentNumber` (all occurrences)

---

### Task 4: Rename Application Service

**Files:**
- Modify: `src/main/java/com/wpanther/notification/application/service/NotificationService.java`

- [ ] **Step 1: Rename all references in NotificationService.java**

Apply these `replace_all` replacements:
- `findByInvoiceId` → `findByDocumentId`
- `setInvoiceId(` → `setDocumentId(`
- `setInvoiceNumber(` → `setDocumentNumber(`
- `getInvoiceId()` → `getDocumentId()`
- `getInvoiceNumber()` → `getDocumentNumber()`
- `getTaxInvoiceId()` → `getDocumentId()`
- `getTaxInvoiceNumber()` → `getDocumentNumber()`

Apply these targeted replacements in log messages and template variable keys:
- `"invoiceId"` → `"documentId"` (template variable key)
- `"invoiceNumber"` → `"documentNumber"` (template variable key)
- `"invoiceId={}` → `"documentId={}` (in log format strings)
- `"invoiceNumber={}` → `"documentNumber={}` (in log format strings)
- `invoiceId={}, invoiceNumber={}` → `documentId={}, documentNumber={}` (in log format strings)

Also update log messages that reference "Invoice" to "Document":
- `"Processing InvoiceProcessedEvent:"` → `"Processing InvoiceProcessedEvent:"` (keep — class name)
- `"Tax Invoice Processed: "` → `"Tax Invoice Processed: "` (keep — domain term in subject)
- `"PDF Invoice Ready: "` → `"PDF Invoice Ready: "` (keep)
- `"PDF Invoice Signed: "` → `"PDF Invoice Signed: "` (keep)
- `"XML Document Signed: "` → `"XML Document Signed: "` (keep)
- `"Tax Invoice PDF Ready: "` → `"Tax Invoice PDF Ready: "` (keep)
- `"Saga started: sagaId={}, documentType={}, invoiceNumber={}"` → `"Saga started: sagaId={}, documentType={}, documentNumber={}"`
- `"Processing TaxInvoicePdfGeneratedEvent: taxInvoiceId={}, taxInvoiceNumber={}"` → `"Processing TaxInvoicePdfGeneratedEvent: documentId={}, documentNumber={}"`
- `"Processing EbmsSentEvent: documentId={}, invoiceId={}, invoiceNumber={}"` → `"Processing EbmsSentEvent: documentId={}, documentNumber={}"`

---

### Task 5: Rename Infrastructure Adapters

**Files:**
- Modify: `src/main/java/com/wpanther/notification/infrastructure/adapter/in/rest/NotificationController.java`
- Modify: `src/main/java/com/wpanther/notification/infrastructure/adapter/in/kafka/NotificationEventRoutes.java`
- Modify: `src/main/java/com/wpanther/notification/infrastructure/adapter/out/notification/WebhookNotificationSenderAdapter.java`

- [ ] **Step 1: Rename in `NotificationController.java`**

Apply replacements:
- `setInvoiceId(` → `setDocumentId(`
- `setInvoiceNumber(` → `setDocumentNumber(`
- `findByInvoiceId` → `findByDocumentId`
- `@GetMapping("/invoice/{invoiceId}")` → `@GetMapping("/document/{documentId}")`
- `@PathVariable String invoiceId` → `@PathVariable String documentId`
- `queryNotificationUseCase.findByInvoiceId(invoiceId,` → `queryNotificationUseCase.findByDocumentId(documentId,`
- In `NotificationRequest` record: `String invoiceId,` → `String documentId,` and `String invoiceNumber,` → `String documentNumber,`
- `request.invoiceId()` → `request.documentId()`
- `request.invoiceNumber()` → `request.documentNumber()`
- Update Javadoc: `"Get notifications by invoice ID"` → `"Get notifications by document ID"`

- [ ] **Step 2: Rename in `NotificationEventRoutes.java`**

Apply `replace_all`:
- `"invoiceNumber"` → `"documentNumber"` (Camel header names and log messages)
- `getInvoiceNumber()` → `getDocumentNumber()`
- `getInvoiceId()` → `getDocumentId()`
- `getTaxInvoiceNumber()` → `getDocumentNumber()`
- `getTaxInvoiceId()` → `getDocumentId()`

Also update log messages:
- `"Created notification for invoice processed: ${header.invoiceNumber}"` → `"Created notification for invoice processed: ${header.documentNumber}"`
- `"Created notification for tax invoice processed: ${header.invoiceNumber}"` → `"Created notification for tax invoice processed: ${header.documentNumber}"`
- `"Created notification for PDF generated: ${header.invoiceNumber}"` → `"Created notification for PDF generated: ${header.documentNumber}"`
- `"Created notification for PDF signed: ${header.invoiceNumber}"` → `"Created notification for PDF signed: ${header.documentNumber}"`
- `"Created notification for XML signed: ${header.invoiceNumber}"` → `"Created notification for XML signed: ${header.documentNumber}"`
- `"Created notification for ebMS sent: ${header.invoiceNumber}"` → `"Created notification for ebMS sent: ${header.documentNumber}"`
- `"Created notification for tax invoice PDF generated: ${header.taxInvoiceNumber}"` → `"Created notification for tax invoice PDF generated: ${header.documentNumber}"`

- [ ] **Step 3: Rename in `WebhookNotificationSenderAdapter.java`**

Apply replacements:
- `payload.put("invoiceId", notification.getInvoiceId());` → `payload.put("documentId", notification.getDocumentId());`
- `payload.put("invoiceNumber", notification.getInvoiceNumber());` → `payload.put("documentNumber", notification.getDocumentNumber());`

---

### Task 6: Rename Thymeleaf Templates

**Files:**
- Modify: `src/main/resources/templates/invoice-processed.html`
- Modify: `src/main/resources/templates/taxinvoice-processed.html`
- Modify: `src/main/resources/templates/pdf-generated.html`
- Modify: `src/main/resources/templates/taxinvoice-pdf-generated.html`
- Modify: `src/main/resources/templates/pdf-signed.html`
- Modify: `src/main/resources/templates/xml-signed.html`
- Modify: `src/main/resources/templates/ebms-sent.html`
- Modify: `src/main/resources/templates/saga-completed.html`
- Modify: `src/main/resources/templates/saga-failed.html`

- [ ] **Step 1: Rename in `invoice-processed.html`**

- `${invoiceNumber}` → `${documentNumber}`
- `${invoiceId}` → `${documentId}`
- Label `Invoice Number:` → `Document Number:`
- Label `Invoice ID:` → `Document ID:`
- Default placeholder values: `INV-2025-001` → `DOC-2025-001`, `uuid-here` stays

- [ ] **Step 2: Rename in `taxinvoice-processed.html`**

Same changes as invoice-processed.html:
- `${invoiceNumber}` → `${documentNumber}`
- `${invoiceId}` → `${documentId}`
- Label `Invoice Number:` → `Document Number:`
- Label `Invoice ID:` → `Document ID:`

- [ ] **Step 3: Rename in `pdf-generated.html`**

- `${invoiceNumber}` → `${documentNumber}`
- Label `Invoice Number:` → `Document Number:`

- [ ] **Step 4: Rename in `taxinvoice-pdf-generated.html`**

- `${taxInvoiceNumber}` → `${documentNumber}`
- Label `Tax Invoice Number:` → `Document Number:`

- [ ] **Step 5: Rename in `pdf-signed.html`**

- `${invoiceNumber}` → `${documentNumber}`
- Label `Invoice Number:` → `Document Number:`

- [ ] **Step 6: Rename in `xml-signed.html`**

- `${invoiceNumber}` → `${documentNumber}`
- `${invoiceId}` → `${documentId}`
- Label `Invoice Number:` → `Document Number:`
- `th:if="${invoiceId}"` → `th:if="${documentId}"`
- Label `Invoice ID:` → `Document ID:`

- [ ] **Step 7: Rename in `ebms-sent.html`**

- `${invoiceId}` → `${documentId}`
- `${invoiceNumber}` → `${documentNumber}`
- `th:if="${invoiceId != null and invoiceId != 'N/A'}"` → `th:if="${documentId != null and documentId != 'N/A'}"`
- `th:if="${invoiceNumber != null and invoiceNumber != 'N/A'}"` → `th:if="${documentNumber != null and documentNumber != 'N/A'}"`
- Labels: `Invoice ID:` → `Document ID:`, `Invoice Number:` → `Document Number:`

- [ ] **Step 8: Rename in `saga-completed.html`**

- `${invoiceNumber}` → `${documentNumber}`
- Label `Invoice Number:` → `Document Number:`

- [ ] **Step 9: Rename in `saga-failed.html`**

- `${invoiceNumber}` → `${documentNumber}`
- Label `Invoice Number:` → `Document Number:`

---

### Task 7: Rename Unit Tests

**Files:**
- Modify: `src/test/java/com/wpanther/notification/domain/model/NotificationTest.java`
- Modify: `src/test/java/com/wpanther/notification/application/service/NotificationServiceTest.java`
- Modify: `src/test/java/com/wpanther/notification/application/service/NotificationSendingServiceTest.java`
- Modify: `src/test/java/com/wpanther/notification/infrastructure/adapter/in/rest/NotificationControllerTest.java`
- Modify: `src/test/java/com/wpanther/notification/infrastructure/adapter/out/persistence/NotificationRepositoryAdapterTest.java`
- Modify: `src/test/java/com/wpanther/notification/infrastructure/adapter/out/notification/WebhookNotificationSenderAdapterTest.java`
- Modify: `src/test/java/com/wpanther/notification/infrastructure/adapter/out/notification/EmailNotificationSenderAdapterTest.java`
- Modify: `src/test/java/com/wpanther/notification/infrastructure/adapter/out/notification/TemplateEngineTest.java`
- Modify: `src/test/java/com/wpanther/notification/application/port/in/event/XmlSignedEventTest.java`
- Modify: `src/test/java/com/wpanther/notification/application/port/in/event/saga/SagaStartedEventTest.java`
- Modify: `src/test/java/com/wpanther/notification/application/port/in/event/saga/SagaStepCompletedEventTest.java`
- Modify: `src/test/java/com/wpanther/notification/application/port/in/event/saga/SagaCompletedEventTest.java`
- Modify: `src/test/java/com/wpanther/notification/application/port/in/event/saga/SagaFailedEventTest.java`

- [ ] **Step 1: Rename in `NotificationTest.java`**

Apply `replace_all`:
- `.invoiceId(` → `.documentId(`
- `.invoiceNumber(` → `.invoiceNumber(` → `.documentNumber(`
- `getInvoiceId()` → `getDocumentId()`
- `getInvoiceNumber()` → `getDocumentNumber()`
- `"invoiceNumber"` → `"documentNumber"` (in template variable assertions)
- `"invoice-uuid"` → `"document-uuid"` (test data values)

- [ ] **Step 2: Rename in `NotificationServiceTest.java`**

Apply replacements:
- `findByInvoiceId` → `findByDocumentId`
- `getInvoiceNumber()` → `getDocumentNumber()`
- Test data values: `"TAXINV-2025-001"` stays (it's a document number value, not a field name)

- [ ] **Step 3: Rename in `NotificationSendingServiceTest.java`**

Apply replacement:
- `"invoiceNumber"` → `"documentNumber"` (template variable key in test data)

- [ ] **Step 4: Rename in `NotificationControllerTest.java`**

Apply replacements:
- `findByInvoiceId` → `findByDocumentId`
- `"invoiceId"` → `"documentId"` (JSON keys and test data)
- `"invoiceNumber"` → `"documentNumber"` (JSON keys and test data)
- `"invoice-uuid"` → `"document-uuid"` (test data values)
- `/invoice/` → `/document/` (REST endpoint paths in MockMvc calls)
- `@DisplayName` strings: `"invoice"` → `"document"` (in endpoint test descriptions)

- [ ] **Step 5: Rename in `NotificationRepositoryAdapterTest.java`**

Apply replacements:
- `setInvoiceId(` → `setDocumentId(`
- `setInvoiceNumber(` → `setDocumentNumber(`
- `findByInvoiceId` → `findByDocumentId`
- `findByInvoiceNumber` → `findByDocumentNumber`
- `getInvoiceNumber()` → `getDocumentNumber()`
- Test data values like `"INV-001"`, `"INV-2024-001"` stay (they're document number values)

- [ ] **Step 6: Rename in `WebhookNotificationSenderAdapterTest.java`**

Apply replacements:
- `.invoiceId(` → `.documentId(`
- `.invoiceNumber(` → `.documentNumber(`
- `"invoiceId"` → `"documentId"` (JSON assertion keys)
- `"invoiceNumber"` → `"documentNumber"` (template variable keys and JSON assertion keys)
- `"INV-123"` → `"DOC-123"` (test data values, optional but consistent)
- `"INV-001"` → `"DOC-001"` (test data values, optional but consistent)

- [ ] **Step 7: Rename in `EmailNotificationSenderAdapterTest.java`**

Apply replacements:
- `"invoiceNumber"` → `"documentNumber"` (template variable key)
- `"invoiceId"` → `"documentId"` (metadata key and header assertion)
- `X-invoiceId` → `X-documentId` (email header name)

- [ ] **Step 8: Rename in `TemplateEngineTest.java`**

Apply replacements:
- `"invoiceNumber"` → `"documentNumber"` (template variable key)
- Update `@DisplayName` if it references `invoiceId`

- [ ] **Step 9: Rename in `XmlSignedEventTest.java`**

Apply replacements:
- `"invoiceId"` → `"documentId"` (JSON fixture keys)
- `"invoiceNumber"` → `"documentNumber"` (JSON fixture keys)
- `getInvoiceId()` → `getDocumentId()`
- `getInvoiceNumber()` → `getDocumentNumber()`
- Test values like `"doc-123"`, `"INV-001"` stay (they're document ID/number values)

- [ ] **Step 10: Rename in `SagaStartedEventTest.java`**

Apply replacements:
- `"invoiceNumber"` → `"documentNumber"` (JSON fixture keys)
- `getInvoiceNumber()` → `getDocumentNumber()`
- Remove any `"startedAt"` references from JSON fixtures and assertions

- [ ] **Step 11: Rename in `SagaStepCompletedEventTest.java`**

Apply replacements:
- Remove all `"documentId"`, `"invoiceNumber"`, `"stepDurationMs"` references from JSON fixtures and assertions (orchestrator no longer sends these)
- Remove assertions for these fields

- [ ] **Step 12: Rename in `SagaCompletedEventTest.java`**

Apply replacements:
- `"invoiceNumber"` → `"documentNumber"` (JSON fixture keys)
- `getInvoiceNumber()` → `getDocumentNumber()`

- [ ] **Step 13: Rename in `SagaFailedEventTest.java`**

Apply replacements:
- `"invoiceNumber"` → `"documentNumber"` (JSON fixture keys)
- `getInvoiceNumber()` → `getDocumentNumber()`

---

### Task 8: Rename Integration Tests

**Files:**
- Modify: `src/test/java/com/wpanther/notification/integration/AbstractKafkaConsumerTest.java`
- Modify: `src/test/java/com/wpanther/notification/integration/KafkaConsumerIntegrationTest.java`

- [ ] **Step 1: Rename in `AbstractKafkaConsumerTest.java`**

Apply replacements:
- `awaitNotificationByInvoiceId` → `awaitNotificationByDocumentId`
- `getNotificationByInvoiceId` → `getNotificationByDocumentId`
- `invoice_id` → `document_id` (SQL WHERE clause)

- [ ] **Step 2: Rename in `KafkaConsumerIntegrationTest.java`**

Apply replacements:
- `String invoiceId` → `String documentId` (variable declarations)
- `String invoiceNumber` → `String documentNumber` (variable declarations)
- `awaitNotificationByInvoiceId(` → `awaitNotificationByDocumentId(`
- `"invoice_id"` → `"document_id"` (SQL assertion keys)
- `"invoice_number"` → `"document_number"` (SQL assertion keys)

Also update JSON event construction where fields are renamed:
- For events that now use `documentId`/`documentNumber`, update the JSON keys in `sendEvent()` calls
- For saga events, `"invoiceNumber"` → `"documentNumber"` in JSON payloads

---

### Task 9: Update CLAUDE.md

**Files:**
- Modify: `src/main/java/../CLAUDE.md` → `CLAUDE.md` (project root, relative to notification-service)

- [ ] **Step 1: Update field references in CLAUDE.md**

Apply `replace_all` replacements:
- `invoiceId` → `documentId`
- `invoiceNumber` → `documentNumber`
- `invoice_id` → `document_id`
- `findByInvoiceId` → `findByDocumentId`
- `/invoice/{invoiceId}` → `/document/{documentId}`
- `getInvoiceId()` → `getDocumentId()`
- `getInvoiceNumber()` → `getDocumentNumber()`
- `setInvoiceId(` → `setDocumentId(`
- `setInvoiceNumber(` → `setDocumentNumber(`

Also update the event DTO template example code block to show `documentId`/`documentNumber` instead of `invoiceId`/`invoiceNumber`.

---

### Task 10: Build Verification and Commit

- [ ] **Step 1: Run unit tests**

Run: `cd /home/wpanther/projects/etax/invoice-microservices/services/notification-service && mvn clean test`
Expected: All tests pass (integration tests excluded by default)

- [ ] **Step 2: Run full build with package**

Run: `mvn clean package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Verify no remaining references**

Run: `grep -rn "invoiceId\|invoiceNumber\|invoice_id\|invoice-number\|getInvoiceId\|setInvoiceId\|getInvoiceNumber\|setInvoiceNumber\|findByInvoiceId\|findByInvoiceNumber\|taxInvoiceId\|taxInvoiceNumber" src/main/java src/test/java src/main/resources --include="*.java" --include="*.html" --include="*.sql" --include="*.yml" --include="*.md"`
Expected: No matches

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: rename invoiceId/invoiceNumber to documentId/documentNumber

All producing services have switched to documentId/documentNumber.
This is a full atomic rename across all layers:

- Event DTOs: invoiceId→documentId, invoiceNumber→documentNumber
  (removed duplicate invoiceId from EbmsSentEvent, InvoicePdfGeneratedEvent;
   removed duplicate taxInvoiceId from TaxInvoicePdfGeneratedEvent;
   removed stale fields from SagaStartedEvent, SagaStepCompletedEvent)
- Domain model: field, getter, setter, builder renames
- Repository: findByInvoiceId→findByDocumentId, findByInvoiceNumber→findByDocumentNumber
- JPA entity: invoice_id→document_id column mapping
- REST API: /invoice/{invoiceId}→/document/{documentId}
- Kafka routes: header names and event getter calls
- Webhook sender: payload keys
- Thymeleaf templates: variable names and display labels
- Database: consolidated V1 migration with document_id/document_number
- Tests: all ~15 test files updated
```
