# Design: Rename invoiceId/invoiceNumber to documentId/documentNumber

**Date:** 2026-03-30
**Scope:** notification-service (full rename across all layers)
**Trigger:** All producing services (orchestrator, invoice-processing, taxinvoice-processing, xml-signing, pdf-signing, ebms-sending, pdf-generation) are renaming their event fields from `invoiceId`/`invoiceNumber` to `documentId`/`documentNumber`.

## Approach

Single atomic refactor (Approach A). Mechanical rename across all layers in one pass. No intermediate mapping or dual-field states.

## Changes by Layer

### 1. Event DTOs (11 files)

Rename `invoiceId` → `documentId` and `invoiceNumber` → `documentNumber` in `@JsonProperty`, field names, constructors, and getters across all event DTOs.

| Event DTO | Changes |
|-----------|---------|
| `InvoiceProcessedEvent` | `invoiceId` → `documentId`, `invoiceNumber` → `documentNumber` |
| `TaxInvoiceProcessedEvent` | `invoiceId` → `documentId`, `invoiceNumber` → `documentNumber` |
| `InvoicePdfGeneratedEvent` | Remove `invoiceId` field (duplicate of existing `documentId`), rename `invoiceNumber` → `documentNumber` |
| `TaxInvoicePdfGeneratedEvent` | Remove `taxInvoiceId` field (duplicate of existing `documentId`), rename `taxInvoiceNumber` → `documentNumber` |
| `PdfSignedEvent` | `invoiceId` → `documentId`, `invoiceNumber` → `documentNumber` |
| `XmlSignedEvent` | `invoiceId` → `documentId`, `invoiceNumber` → `documentNumber` |
| `EbmsSentEvent` | Remove `invoiceId` field (producer no longer sends it; `documentId` is the source of truth), rename `invoiceNumber` → `documentNumber` |
| `DocumentReceivedEvent` | Rename `invoiceNumber` → `documentNumber` (already has `documentId`) |
| `SagaStartedEvent` | Rename `invoiceNumber` → `documentNumber`, **remove stale `startedAt` field** |
| `SagaStepCompletedEvent` | **Remove stale fields:** `documentId`, `invoiceNumber`, `stepDurationMs` |
| `SagaCompletedEvent` | Rename `invoiceNumber` → `documentNumber` |
| `SagaFailedEvent` | Rename `invoiceNumber` → `documentNumber` |

**Stale field cleanup rationale:** The orchestrator no longer sends `startedAt` on `SagaStartedEvent`, and no longer sends `documentId`/`invoiceNumber`/`stepDurationMs` on `SagaStepCompletedEvent`. These fields always deserialize as `null`.

### 2. Domain Model (`Notification.java`)

- Field `invoiceId` → `documentId`
- Field `invoiceNumber` → `documentNumber`
- Getters: `getInvoiceId()` → `getDocumentId()`, `getInvoiceNumber()` → `getDocumentNumber()`
- Setters: `setInvoiceId()` → `setDocumentId()`, `setInvoiceNumber()` → `setDocumentNumber()`
- Builder: `invoiceId()` → `documentId()`, `invoiceNumber()` → `documentNumber()`

### 3. Repository Layer

**`NotificationRepository.java` (domain interface):**
- `findByInvoiceId()` → `findByDocumentId()`
- `findByInvoiceNumber()` → `findByDocumentNumber()`

**`JpaNotificationRepository.java` (Spring Data):**
- `findByInvoiceId()` → `findByDocumentId()`
- `findByInvoiceNumber()` → `findByDocumentNumber()`

**`NotificationRepositoryAdapter.java`:**
- All `findByInvoiceId`/`findByInvoiceNumber` → `findByDocumentId`/`findByDocumentNumber`
- Entity mapping: `.invoiceId()` → `.documentId()`, `.invoiceNumber()` → `.documentNumber()`

### 4. Application Layer

**`QueryNotificationUseCase.java`:**
- `findByInvoiceId()` → `findByDocumentId()`

**`NotificationService.java`:**
- `findByInvoiceId()` → `findByDocumentId()`
- All `notification.setInvoiceId()` → `notification.setDocumentId()`
- All `notification.setInvoiceNumber()` → `notification.setDocumentNumber()`
- Template variable keys: `"invoiceId"` → `"documentId"`, `"invoiceNumber"` → `"documentNumber"`
- Log messages updated
- `event.getInvoiceId()` → `event.getDocumentId()`, `event.getInvoiceNumber()` → `event.getDocumentNumber()`
- `event.getTaxInvoiceId()` → `event.getDocumentId()`, `event.getTaxInvoiceNumber()` → `event.getDocumentNumber()`

### 5. Infrastructure - REST API

**`NotificationController.java`:**
- Endpoint: `GET /api/v1/notifications/invoice/{invoiceId}` → `GET /api/v1/notifications/document/{documentId}`
- `CreateNotificationRequest` record: `invoiceId` → `documentId`, `invoiceNumber` → `documentNumber`
- All handler references updated

### 6. Infrastructure - Kafka Routes

**`NotificationEventRoutes.java`:**
- Camel header names: `"invoiceNumber"` → `"documentNumber"`
- `.log()` messages updated
- `event.getInvoiceNumber()` → `event.getDocumentNumber()`
- `event.getInvoiceId()` → `event.getDocumentId()`
- `event.getTaxInvoiceNumber()` → `event.getDocumentNumber()`

### 7. Infrastructure - Webhook Sender

**`WebhookNotificationSenderAdapter.java`:**
- Payload keys: `"invoiceId"` → `"documentId"`, `"invoiceNumber"` → `"documentNumber"`

### 8. Infrastructure - Persistence Entity

**`NotificationEntity.java`:**
- `@Column(name = "invoice_id")` → `@Column(name = "document_id")`
- Field `invoiceNumber` → `documentNumber`
- Index name updated in `@Index` annotation

### 9. Database Migration

Consolidate all existing Flyway migrations (V1–V4) into a single `V1__create_notifications_table.sql` with `document_id`/`document_number` column names. Delete V2, V3, V4 migration files.

The consolidated V1 script will include:
- `notifications` table with `document_id` and `document_number` columns
- `outbox_events` table (from V2)
- Composite index on `(status, created_at)` (from V3)
- `TIMESTAMPTZ` types for timestamp columns (from V4)
- Indexes: `idx_notifications_document_id` on `document_id`

### 10. Thymeleaf Templates (8 files)

All template variables and display labels renamed:

| Template | Changes |
|----------|---------|
| `invoice-processed.html` | `${invoiceNumber}` → `${documentNumber}`, `${invoiceId}` → `${documentId}`, label "Invoice Number" → "Document Number", label "Invoice ID" → "Document ID" |
| `taxinvoice-processed.html` | Same as above |
| `pdf-generated.html` | `${invoiceNumber}` → `${documentNumber}` |
| `taxinvoice-pdf-generated.html` | `${taxInvoiceNumber}` → `${documentNumber}`, label "Tax Invoice Number" → "Document Number" |
| `pdf-signed.html` | `${invoiceNumber}` → `${documentNumber}` |
| `xml-signed.html` | `${invoiceNumber}` → `${documentNumber}`, `${invoiceId}` → `${documentId}` |
| `ebms-sent.html` | `${invoiceId}` → `${documentId}`, `${invoiceNumber}` → `${documentNumber}` |
| `saga-completed.html` | `${invoiceNumber}` → `${documentNumber}`, label "Invoice Number" → "Document Number" |
| `saga-failed.html` | `${invoiceNumber}` → `${documentNumber}`, label "Invoice Number" → "Document Number" |

### 11. Tests (~15 files)

All test files updated with matching renames:
- JSON fixtures use `"documentId"`/`"documentNumber"` keys
- Assertions use `getDocumentId()`/`getDocumentNumber()`
- SQL queries use `document_id`/`document_number` columns
- Builder calls use `.documentId()`/`.documentNumber()`
- REST endpoint paths updated to `/document/{documentId}`
- Test data variable names: `invoiceId` → `documentId`, `invoiceNumber` → `documentNumber`

### 12. Documentation

Update `CLAUDE.md` with renamed field names, column names, endpoint paths, and template variable tables. Other `.md` files are excluded from git via `.gitignore`.

## File Count Summary

| Category | Files |
|----------|-------|
| Event DTOs | 11 |
| Domain model | 1 |
| Repository (domain + Spring Data + adapter) | 3 |
| Application service + use case | 2 |
| REST controller | 1 |
| Kafka routes | 1 |
| Webhook sender | 1 |
| JPA entity | 1 |
| Database migrations | 1 (consolidated) + 3 (deleted) |
| Thymeleaf templates | 9 |
| Unit tests | ~15 |
| Integration tests | 2 |
| Documentation | 1 (CLAUDE.md) |

## What This Does NOT Change

- The `Notification` aggregate's state machine (`PENDING → SENDING → SENT → FAILED → RETRYING`)
- The `NotificationSender` strategy pattern (Email + Webhook)
- The `NotificationDispatcherService` async dispatch mechanism
- Kafka topic names or subscription configuration
- The `NotificationType` or `NotificationChannel` enums
- The outbox pattern infrastructure
