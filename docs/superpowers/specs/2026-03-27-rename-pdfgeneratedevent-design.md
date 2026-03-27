# Design: Rename PdfGeneratedEvent → InvoicePdfGeneratedEvent

**Date:** 2026-03-27
**Scope:** notification-service only
**Type:** Symbol rename — no logic, schema, or topic changes

## Motivation

`TaxInvoicePdfGeneratedEvent` was added to handle the `pdf.generated.tax-invoice` topic. Its sibling event for invoice PDFs (`pdf.generated.invoice`) is named `PdfGeneratedEvent`, which is ambiguous and asymmetric. Renaming to `InvoicePdfGeneratedEvent` makes the naming convention consistent across both PDF generation events.

## Approach

Full rename everywhere: class file, all Java source references, associated method names, and all documentation files including archived plan docs.

No Kafka topic names, template names, YAML config, or database schema are affected.

## Changes

### 1. Class File

| Before | After |
|--------|-------|
| `PdfGeneratedEvent.java` | `InvoicePdfGeneratedEvent.java` |
| `public class PdfGeneratedEvent` | `public class InvoicePdfGeneratedEvent` |

No field, constructor, or logic changes.

### 2. Java Sources (5 files)

| File | Changes |
|------|---------|
| `application/port/in/event/InvoicePdfGeneratedEvent.java` | Class renamed (file rename) |
| `application/usecase/ProcessingEventUseCase.java` | Import + `handlePdfGenerated(PdfGeneratedEvent)` → `handleInvoicePdfGenerated(InvoicePdfGeneratedEvent)` |
| `application/service/NotificationService.java` | Import + method signature + log message |
| `infrastructure/adapter/in/kafka/NotificationEventRoutes.java` | Import + `.unmarshal(…, InvoicePdfGeneratedEvent.class)` + log strings + handler method name + body cast |
| `NotificationServiceTest.java` | Import + instantiation + `testHandlePdfGenerated_dispatchesAsync` → `testHandleInvoicePdfGenerated_dispatchesAsync` |
| `KafkaConsumerIntegrationTest.java` | Import + `shouldConsumePdfGeneratedEvent` → `shouldConsumeInvoicePdfGeneratedEvent` + `@DisplayName` + instantiation |

### 3. Documentation (6 files)

| File | Changes |
|------|---------|
| `README.md` | Consumed Topics table row; event schema heading + `eventType` value; integration test name |
| `CLAUDE.md` | Route table row; processing events list; integration test list |
| `PROGRAM_FLOW.md` | All occurrences of `PdfGeneratedEvent` and `handlePdfGenerated` |
| `IMPLEMENTATION_SUMMARY.md` | All occurrences of `PdfGeneratedEvent` and `handlePdfGenerated` |
| `docs/plans/2026-03-09-hexagonal-architecture-migration-design.md` | File tree entry |
| `docs/plans/2026-03-03-hexagonal-architecture-migration.md` | All occurrences of `PdfGeneratedEvent` and `handlePdfGenerated` |

## Out of Scope

- Kafka topic names (`pdf.generated.invoice` unchanged)
- Email template names (`pdf-generated.html` unchanged)
- YAML config keys (`pdf-generated` in `kafka.topics` unchanged)
- Database schema (no changes)
- Any changes to `TaxInvoicePdfGeneratedEvent`

## Verification

After the rename, `mvn test` must pass with 147 unit tests (same count as before).
