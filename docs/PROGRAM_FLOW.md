# Program Flow

This document describes the notification processing flows in the Notification Service.

## Overview

The service processes notifications through three entry points:
1. **Kafka Events** (via Apache Camel routes) - Automatic notifications triggered by invoice processing events
2. **Saga Lifecycle Events** (via Apache Camel routes) - Notifications for saga orchestration completion/failure
3. **REST API** - Manual notification requests

**Technology**: All Kafka consumption uses **Apache Camel 4.14.4** RouteBuilder (16 routes total), not Spring Kafka.

## Flow 1: Kafka Event-Driven Notifications

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    KAFKA EVENT FLOW (Apache Camel)                          │
└─────────────────────────────────────────────────────────────────────────────┘

  Kafka Topics               NotificationEventRoutes              NotificationService
  ────────────               ───────────────────────              ───────────────────
       │                                  │                                │
       │  invoice.received                │                                │
       │  invoice.processed               │                                │
       │  pdf.generated                   │                                │
       │                                  │                                │
       ├─────────────────────────────────►│                                │
       │        Event Message             │                                │
       │                                  │                                │
       │                          ┌───────┴───────┐                        │
       │                          │ Check if      │                        │
       │                          │ notifications │                        │
       │                          │ enabled       │                        │
       │                          └───────┬───────┘                        │
       │                                  │                                │
       │                          ┌───────┴───────┐                        │
       │                          │ Extract event │                        │
       │                          │ data into     │                        │
       │                          │ templateVars  │                        │
       │                          └───────┬───────┘                        │
       │                                  │                                │
       │                          ┌───────┴───────┐                        │
       │                          │ Create        │                        │
       │                          │ Notification  │                        │
       │                          │ aggregate     │                        │
       │                          └───────┬───────┘                        │
       │                                  │                                │
       │                                  ├───────────────────────────────►│
       │                                  │  sendNotificationAsync()       │
       │                                  │                                │
       │                                  │                        [See Flow 3]
```

### Event-to-Template Mapping

**Processing Events (Creates Email Notifications):**

| Kafka Topic | Event Class (extends IntegrationEvent) | Camel Route | Template | Subject Pattern |
|-------------|----------------------------------------|-------------|----------|-----------------|
| `invoice.processed` | InvoiceProcessedEvent | notification-invoice-processed | invoice-processed.html | "Invoice Processed: {invoiceNumber}" |
| `taxinvoice.processed` | TaxInvoiceProcessedEvent | notification-taxinvoice-processed | taxinvoice-processed.html | "Tax Invoice Processed: {invoiceNumber}" |
| `pdf.generated` | PdfGeneratedEvent | notification-pdf-generated | pdf-generated.html | "PDF Invoice Ready: {invoiceNumber}" |
| `pdf.signed` | PdfSignedEvent | notification-pdf-signed | pdf-signed.html | "Signed PDF Ready: {invoiceNumber}" |
| `ebms.sent` | EbmsSentEvent | notification-ebms-sent | ebms-sent.html | "Document Submitted to TRD: {invoiceNumber}" |

**Saga Lifecycle Events:**

| Kafka Topic | Event Class (extends IntegrationEvent) | Camel Route | Template | Action |
|-------------|----------------------------------------|-------------|----------|--------|
| `saga.lifecycle.started` | SagaStartedEvent | notification-saga-started | N/A | Logging only |
| `saga.lifecycle.step-completed` | SagaStepCompletedEvent | notification-saga-step-completed | N/A | Logging only |
| `saga.lifecycle.completed` | SagaCompletedEvent | notification-saga-completed | saga-completed.html | Email notification (success theme) |
| `saga.lifecycle.failed` | SagaFailedEvent | notification-saga-failed | saga-failed.html | URGENT email notification (alert theme) |

**Statistics Events (Logging Only):**
- `document.received.*` topics → 7 Camel routes for document counting and statistics

### Template Variables by Event Type

**InvoiceReceivedEvent:**
- `invoiceId`, `invoiceNumber`, `receivedAt`, `source`

**InvoiceProcessedEvent:**
- `invoiceId`, `invoiceNumber`, `totalAmount`, `currency`, `processedAt` (from occurredAt)

**PdfGeneratedEvent:**
- `invoiceId`, `invoiceNumber`, `documentId`, `documentUrl`, `fileSize`, `generatedAt` (from occurredAt), `xmlEmbedded`, `digitallySigned`

**SagaCompletedEvent:**
- `sagaId`, `documentId`, `invoiceNumber`, `documentType`, `stepsExecuted`, `durationSec`, `completedAt`

**SagaFailedEvent:**
- `sagaId`, `documentId`, `invoiceNumber`, `documentType`, `failedStep`, `errorMessage`, `retryCount`, `compensationInitiated`, `failedAt`

---

## Flow 2: REST API Manual Notifications

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           REST API FLOW                                      │
└─────────────────────────────────────────────────────────────────────────────┘

  HTTP Client              NotificationController           NotificationService
  ───────────              ──────────────────────           ───────────────────
       │                            │                               │
       │  POST /api/v1/notifications│                               │
       │  {type, channel, recipient,│                               │
       │   templateName, ...}       │                               │
       ├───────────────────────────►│                               │
       │                            │                               │
       │                    ┌───────┴───────┐                       │
       │                    │ Check if      │                       │
       │                    │ templateName  │                       │
       │                    │ provided      │                       │
       │                    └───────┬───────┘                       │
       │                            │                               │
       │                    ┌───────┴───────┐                       │
       │                    │ Create        │                       │
       │                    │ Notification  │                       │
       │                    │ (template or  │                       │
       │                    │  plain text)  │                       │
       │                    └───────┬───────┘                       │
       │                            │                               │
       │                            ├──────────────────────────────►│
       │                            │    sendNotification()         │
       │                            │                               │
       │                            │                       [See Flow 3]
       │                            │                               │
       │                            │◄──────────────────────────────┤
       │                            │    Notification (updated)     │
       │                            │                               │
       │◄───────────────────────────┤                               │
       │  {notificationId, status,  │                               │
       │   createdAt, sentAt}       │                               │
```

---

## Flow 3: Notification Sending (Core Flow)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      NOTIFICATION SENDING FLOW                               │
└─────────────────────────────────────────────────────────────────────────────┘

NotificationService        NotificationRepository       NotificationSender
───────────────────        ──────────────────────       ──────────────────
       │                            │                          │
       │  1. Save notification      │                          │
       │     (status: PENDING)      │                          │
       ├───────────────────────────►│                          │
       │◄───────────────────────────┤                          │
       │                            │                          │
       │  2. Mark as SENDING        │                          │
       ├───────────────────────────►│                          │
       │◄───────────────────────────┤                          │
       │                            │                          │
       │  3. Find sender for channel│                          │
       │     (EMAIL → EmailSender)  │                          │
       │     (WEBHOOK → WebhookSender)                         │
       │                            │                          │
       │  4. Send notification      │                          │
       ├──────────────────────────────────────────────────────►│
       │                            │                          │
       │                            │              ┌───────────┴───────────┐
       │                            │              │ EMAIL: Render template│
       │                            │              │ and send via SMTP     │
       │                            │              │                       │
       │                            │              │ WEBHOOK: Build JSON   │
       │                            │              │ and POST to URL       │
       │                            │              └───────────┬───────────┘
       │                            │                          │
       │◄──────────────────────────────────────────────────────┤
       │     Success or Exception   │                          │
       │                            │                          │
   ┌───┴───┐                        │                          │
   │Success│                        │                          │
   └───┬───┘                        │                          │
       │  5a. Mark as SENT          │                          │
       ├───────────────────────────►│                          │
       │                            │                          │
   ┌───┴───┐                        │                          │
   │Failure│                        │                          │
   └───┬───┘                        │                          │
       │  5b. Mark as FAILED        │                          │
       │      (store errorMessage)  │                          │
       ├───────────────────────────►│                          │
```

---

## Flow 4: Email Sending Detail

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         EMAIL SENDING FLOW                                   │
└─────────────────────────────────────────────────────────────────────────────┘

EmailNotificationSender        TemplateEngine              JavaMailSender
───────────────────────        ──────────────              ──────────────
       │                             │                           │
       │  1. Check if template-based │                           │
       │                             │                           │
   ┌───┴────────────┐                │                           │
   │Has templateName│                │                           │
   └───┬────────────┘                │                           │
       │                             │                           │
       │  render(templateName, vars) │                           │
       ├────────────────────────────►│                           │
       │                             │                           │
       │                     ┌───────┴───────┐                   │
       │                     │ Create        │                   │
       │                     │ Thymeleaf     │                   │
       │                     │ Context       │                   │
       │                     └───────┬───────┘                   │
       │                             │                           │
       │                     ┌───────┴───────┐                   │
       │                     │ Process       │                   │
       │                     │ template      │                   │
       │                     │ HTML          │                   │
       │                     └───────┬───────┘                   │
       │                             │                           │
       │◄────────────────────────────┤                           │
       │        HTML body            │                           │
       │                             │                           │
       │  2. Create MimeMessage      │                           │
       │     - Set recipient         │                           │
       │     - Set subject           │                           │
       │     - Set HTML body         │                           │
       │     - Add X-* headers       │                           │
       │       from metadata         │                           │
       │                             │                           │
       │  3. Send via SMTP           │                           │
       ├─────────────────────────────────────────────────────────►│
       │                             │                           │
       │◄─────────────────────────────────────────────────────────┤
       │        Success/MessagingException                       │
```

---

## Flow 5: Webhook Sending Detail

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        WEBHOOK SENDING FLOW                                  │
└─────────────────────────────────────────────────────────────────────────────┘

WebhookNotificationSender                    WebClient
─────────────────────────                    ─────────
       │                                         │
       │  1. Build JSON payload                  │
       │     {                                   │
       │       notificationId,                   │
       │       type,                             │
       │       subject,                          │
       │       invoiceId,                        │
       │       invoiceNumber,                    │
       │       correlationId,                    │
       │       timestamp,                        │
       │       metadata,                         │
       │       data (templateVariables)          │
       │     }                                   │
       │                                         │
       │  2. POST to recipient URL               │
       ├────────────────────────────────────────►│
       │                                         │
       │                                 ┌───────┴───────┐
       │                                 │ HTTP POST     │
       │                                 │ Content-Type: │
       │                                 │ application/  │
       │                                 │ json          │
       │                                 │               │
       │                                 │ Timeout: 30s  │
       │                                 └───────┬───────┘
       │                                         │
       │  3. Check response status               │
       │◄────────────────────────────────────────┤
       │                                         │
   ┌───┴───────┐                                 │
   │HTTP 2xx   │ Success                         │
   └───────────┘                                 │
   ┌───┴───────┐                                 │
   │HTTP 4xx/5xx│ Error → RuntimeException       │
   └───────────┘                                 │
```

---

## Flow 6: Retry Mechanism

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          RETRY FLOW                                          │
└─────────────────────────────────────────────────────────────────────────────┘

                         Scheduled Task (every 5 minutes)
                         ────────────────────────────────
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     retryFailedNotifications()                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
                    ┌─────────────────────────────────┐
                    │ Query: findFailedNotifications  │
                    │ WHERE status = 'FAILED'         │
                    │ AND retryCount < maxRetries (3) │
                    └─────────────────┬───────────────┘
                                      │
                                      ▼
                    ┌─────────────────────────────────┐
                    │ For each failed notification:   │
                    │                                 │
                    │ 1. notification.prepareRetry()  │
                    │    - status → RETRYING          │
                    │    - retryCount++               │
                    │                                 │
                    │ 2. repository.save()            │
                    │                                 │
                    │ 3. sendNotificationAsync()      │
                    │    [Goes to Flow 3]             │
                    └─────────────────────────────────┘


                         Scheduled Task (every 1 minute)
                         ────────────────────────────────
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    processPendingNotifications()                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
                    ┌─────────────────────────────────┐
                    │ Query: findPendingNotifications │
                    │ WHERE status = 'PENDING'        │
                    └─────────────────┬───────────────┘
                                      │
                                      ▼
                    ┌─────────────────────────────────┐
                    │ For each pending notification:  │
                    │                                 │
                    │ sendNotificationAsync()         │
                    │ [Goes to Flow 3]                │
                    └─────────────────────────────────┘
```

---

## State Machine

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     NOTIFICATION STATE MACHINE                               │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────┐
                              │ PENDING │ (Initial state)
                              └────┬────┘
                                   │
                          markSending()
                                   │
                                   ▼
                              ┌─────────┐
                              │ SENDING │
                              └────┬────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
               markSent()          │        markFailed(msg)
                    │              │              │
                    ▼              │              ▼
               ┌────────┐          │         ┌────────┐
               │  SENT  │          │         │ FAILED │
               └────────┘          │         └────┬───┘
              (Terminal)           │              │
                                   │       canRetry(max)?
                                   │       prepareRetry()
                                   │              │
                                   │              ▼
                                   │        ┌──────────┐
                                   │        │ RETRYING │
                                   │        └────┬─────┘
                                   │             │
                                   └─────────────┘
                                   (back to SENDING)

State Transitions:
- PENDING → SENDING    : markSending()
- RETRYING → SENDING   : markSending()
- SENDING → SENT       : markSent()
- SENDING → FAILED     : markFailed(errorMessage)
- FAILED → RETRYING    : prepareRetry() [if retryCount < maxRetries]
```

---

## Component Interaction Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       COMPONENT INTERACTIONS                                 │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                              PRESENTATION LAYER                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                     NotificationController                               │ │
│  │  POST /notifications  GET /{id}  GET /statistics  POST /{id}/retry      │ │
│  └────────────────────────────────────┬────────────────────────────────────┘ │
└───────────────────────────────────────┼──────────────────────────────────────┘
                                        │
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                              APPLICATION LAYER                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                       NotificationService                                │ │
│  │  sendNotification()  sendNotificationAsync()  getStatistics()           │ │
│  │  retryFailedNotifications() [scheduled]                                 │ │
│  │  processPendingNotifications() [scheduled]                              │ │
│  └────────────────────────────────────┬────────────────────────────────────┘ │
└───────────────────────────────────────┼──────────────────────────────────────┘
                                        │
                 ┌──────────────────────┼──────────────────────┐
                 │                      │                      │
                 ▼                      ▼                      ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                                DOMAIN LAYER                                  │
│  ┌───────────────────┐  ┌─────────────────────┐  ┌────────────────────────┐ │
│  │   Notification    │  │NotificationRepository│  │  NotificationSender   │ │
│  │   (Aggregate)     │  │    (Interface)       │  │    (Interface)        │ │
│  │                   │  │                      │  │                       │ │
│  │ markSending()     │  │ save()               │  │ send(notification)    │ │
│  │ markSent()        │  │ findById()           │  │ supports(channel)     │ │
│  │ markFailed()      │  │ findByStatus()       │  │                       │ │
│  │ prepareRetry()    │  │ findFailedNotifs()   │  │                       │ │
│  │ canRetry()        │  │ countByStatus()      │  │                       │ │
│  └───────────────────┘  └─────────────────────┘  └────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
                 │                      │                      │
                 │                      │                      │
                 ▼                      ▼                      ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                            INFRASTRUCTURE LAYER                              │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                         PERSISTENCE                                      │ │
│  │  NotificationRepositoryImpl ──► JpaNotificationRepository ──► PostgreSQL │ │
│  │  NotificationEntity (JPA mapping with JSONB)                            │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                         NOTIFICATION SENDERS                             │ │
│  │  ┌──────────────────────────┐    ┌───────────────────────────┐          │ │
│  │  │  EmailNotificationSender │    │  WebhookNotificationSender│          │ │
│  │  │  ┌────────────────────┐  │    │  ┌─────────────────────┐  │          │ │
│  │  │  │   TemplateEngine   │  │    │  │     WebClient       │  │          │ │
│  │  │  │   (Thymeleaf)      │  │    │  │     (Reactive)      │  │          │ │
│  │  │  └────────────────────┘  │    │  └─────────────────────┘  │          │ │
│  │  │  ┌────────────────────┐  │    │                           │          │ │
│  │  │  │   JavaMailSender   │  │    │                           │          │ │
│  │  │  │   (SMTP)           │  │    │                           │          │ │
│  │  │  └────────────────────┘  │    │                           │          │ │
│  │  └──────────────────────────┘    └───────────────────────────┘          │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                    MESSAGING (Apache Camel)                              │ │
│  │  NotificationEventRoutes (16 Camel RouteBuilders)                        │ │
│  │  ◄── Kafka Consumer ◄── Kafka Topics                                    │ │
│  │  - Processing events: invoice.processed, pdf.generated, pdf.signed, etc. │ │
│  │  - Saga lifecycle: saga.lifecycle.completed, saga.lifecycle.failed       │ │
│  │  - Statistics: document.received.* (7 topics)                            │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │              OUTBOX PATTERN (Future Event Publishing)                    │ │
│  │  OutboxEventEntity ──► outbox_events table ──► Debezium CDC ──► Kafka   │ │
│  │  (Currently acts as consumer only; publishing capability ready)          │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Flow 6: Saga Lifecycle Event Processing

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  SAGA LIFECYCLE EVENT FLOW (Apache Camel)                   │
└─────────────────────────────────────────────────────────────────────────────┘

 orchestrator-service        Kafka Topics            NotificationEventRoutes
 ────────────────────        ────────────            ───────────────────────
        │                          │                            │
        │ Saga orchestration       │                            │
        │ completed/failed         │                            │
        │                          │                            │
        ├─────────────────────────►│  saga.lifecycle.completed  │
        │                          │  saga.lifecycle.failed     │
        │                          │                            │
        │                          ├───────────────────────────►│
        │                          │                            │
        │                          │                    ┌───────┴───────┐
        │                          │                    │ Camel route   │
        │                          │                    │ unmarshals    │
        │                          │                    │ JSON to event │
        │                          │                    └───────┬───────┘
        │                          │                            │
        │                          │                    ┌───────┴───────┐
        │                          │                    │ handleSaga    │
        │                          │                    │ Completed/    │
        │                          │                    │ Failed()      │
        │                          │                    └───────┬───────┘
        │                          │                            │
        │                          │                    ┌───────┴───────┐
        │                          │                    │ Create        │
        │                          │                    │ Notification  │
        │                          │                    │ with saga     │
        │                          │                    │ template vars │
        │                          │                    └───────┬───────┘
        │                          │                            │
        │                          │                            ├────────►
        │                          │                            │  sendNotificationAsync()
        │                          │                            │
        │                          │                            │  [Proceeds to Flow 3]
```

### Saga Event Handlers

**SagaStartedEvent** (`notification-saga-started` route):
- **Action**: Logging only (no email sent)
- **Purpose**: Audit trail for saga orchestration start
- **Log Level**: INFO

**SagaStepCompletedEvent** (`notification-saga-step-completed` route):
- **Action**: Logging only (no email sent)
- **Purpose**: Track individual step completion
- **Log Level**: INFO
- **Note**: Too noisy for email notifications

**SagaCompletedEvent** (`notification-saga-completed` route):
- **Action**: Create email notification with **success theme** (green)
- **Template**: `saga-completed.html`
- **Subject**: "Saga Completed: {invoiceNumber}"
- **Variables**: sagaId, documentId, invoiceNumber, documentType, stepsExecuted, durationSec, completedAt
- **Notification Type**: `SAGA_COMPLETED`

**SagaFailedEvent** (`notification-saga-failed` route):
- **Action**: Create **URGENT** email notification with **alert theme** (red)
- **Template**: `saga-failed.html`
- **Subject**: "URGENT: Saga Failed - {invoiceNumber}"
- **Variables**: sagaId, documentId, invoiceNumber, documentType, failedStep, errorMessage, retryCount, compensationInitiated, failedAt
- **Notification Type**: `SAGA_FAILED`
- **Special**: Includes compensation status badge if compensation initiated

---

## Error Handling Summary

| Component | Error Type | Handling |
|-----------|------------|----------|
| InvoiceEventListener | Any exception | Log error, don't rethrow (continues processing) |
| NotificationService | Send failure | Mark as FAILED, store errorMessage, scheduled retry |
| EmailNotificationSender | MessagingException | Wrap in NotificationException |
| EmailNotificationSender | TemplateException | Wrap in NotificationException |
| WebhookNotificationSender | HTTP 4xx/5xx | RuntimeException → NotificationException |
| WebhookNotificationSender | Timeout (30s) | TimeoutException → NotificationException |
| TemplateEngine | Any exception | Wrap in TemplateException |

---

## Flow 7: Integration Test Execution

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  INTEGRATION TEST FLOW (TaxInvoiceProcessedEvent)           │
└─────────────────────────────────────────────────────────────────────────────┘

  KafkaConsumerIntegrationTest    TestKafkaProducer          Kafka (9093)
  ────────────────────────────    ─────────────────          ──────────────
       │                                │                          │
       │  1. Create TaxInvoiceProcessedEvent
       │     - invoiceId: "TINV-{UUID}"
       │     - invoiceNumber: "TI0001-{timestamp}"
       │     - total: BigDecimal("25000.00")
       │     - currency: "THB"
       │     - correlationId: UUID
       │                                │                          │
       │  2. sendEvent("taxinvoice.processed", invoiceId, event)
       ├──────────────────────────────►│                          │
       │                                │  3. Serialize to JSON    │
       │                                │     (ObjectMapper)       │
       │                                ├─────────────────────────►│
       │                                │                          │
       │                                │                  ┌───────┴───────┐
       │                                │                  │ TaxInvoice    │
       │                                │                  │ Event stored  │
       │                                │                  │ on topic      │
       │                                │                  └───────────────┘
       │                                │                          │
       │                                │◄─────────────────────────┤
       │                                │  ACK sent                 │
       │                                │                          │
       │                                │                          │
       │                     ┌──────────┴───────────┐
       │                     │  Camel Route:        │
       │                     │  notification-       │
       │                     │  taxinvoice-processed│
       │                     └──────────┬───────────┘
       │                                │
       │                                │  4. Unmarshal JSON to
       │                                │     TaxInvoiceProcessedEvent
       │                                │     (Jackson + JavaTimeModule)
       │                                │
       │                     ┌──────────┴───────────┐
       │                     │  Handle TaxInvoice   │
       │                     │  Processed Event     │
       │                     │  - Create template   │
       │                     │    variables         │
       │                     │  - Create Notification│
       │                     │    aggregate         │
       │                     └──────────┬───────────┘
       │                                │
       │                                │  5. sendNotificationAsync()
       │                                │
       │                                ├─────────────────────────────►
       │                                │                          NotificationService
       │                                │                          ───────────────────
       │                                │                                │
       │                                │                                │  6. Save (PENDING)
       │                                │                                │  7. Mark (SENDING)
       │                                │                                │  8. Mock sender sends
       │                                │                                │  9. Mark (SENT)
       │                                │                                │
       │                                │◄────────────────────────────┘
       │                                │
       │  10. awaitNotificationByInvoiceId(invoiceId)
       │      - Polls database every 1 second
       │      - Waits up to 2 minutes
       │      - Returns when status = SENT
       │
       │  11. Assertions:
       │      - type == "TAXINVOICE_PROCESSED"
       │      - channel == "EMAIL"
       │      - status == "SENT"
       │      - template_name == "taxinvoice-processed"
       │      - invoice_id, invoice_number, correlation_id match
       │      - template_variables contains all expected values
```

### Integration Test Components

**AbstractKafkaConsumerTest** (Base class):
- `testKafkaTemplate` - Spring KafkaTemplate for sending events
- `testJdbcTemplate` - JdbcTemplate for database assertions
- `emailNotificationSender` - @MockBean (returns true for supports(), does nothing on send())
- `webhookNotificationSender` - @MockBean (returns false for supports())
- `objectMapper` - Configured with JavaTimeModule for Instant support

**Helper Methods:**
- `sendEvent(topic, key, event)` - Serializes and sends event to Kafka
- `awaitNotificationByInvoiceId(invoiceId)` - Polls until notification reaches SENT status
- `awaitNotificationByCorrelationId(correlationId)` - Polls by correlation ID
- `awaitNotificationCount(count)` - Polls until expected notification count
- `getNotificationByInvoiceId(invoiceId)` - Retrieves notification from database
- `getNotificationByCorrelationId(correlationId)` - Retrieves by correlation ID
- `assertNoNotificationCreatedAfterWait()` - Asserts no notification created (for logging-only routes)

**Test Configuration:**
- Profile: `consumer-test`
- Kafka: `localhost:9093` (test containers)
- Database: `notification_db` (test containers)
- Spring Boot test: `NONE` (no web environment)

### Integration Test Coverage

| Test Method | Topic | Event Type | Template | Validates |
|-------------|-------|------------|----------|-----------|
| `shouldConsumeInvoiceProcessedEvent()` | `invoice.processed` | InvoiceProcessedEvent | `invoice-processed.html` | Invoice processing flow |
| `shouldConsumeTaxInvoiceProcessedEvent()` | `taxinvoice.processed` | TaxInvoiceProcessedEvent | `taxinvoice-processed.html` | Tax invoice processing flow |

**Common Assertions:**
- Notification type enum value matches event type
- Channel is EMAIL
- Status reaches SENT (async processing completes)
- Recipient is `test-integration@example.com`
- Template name matches expected
- Invoice ID and number preserved
- Correlation ID propagated
- Template variables contain all expected values
- Subject contains invoice number

**Field Difference Note:**
- `InvoiceProcessedEvent` uses `totalAmount` field
- `TaxInvoiceProcessedEvent` uses `total` field
- Both map to template variable `totalAmount` for consistency

---
