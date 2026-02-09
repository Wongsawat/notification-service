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
| `shouldConsumePdfGeneratedEvent()` | `pdf.generated` | PdfGeneratedEvent | `pdf-generated.html` | PDF generation flow |
| `shouldConsumePdfSignedEvent()` | `pdf.signed` | PdfSignedEvent | `pdf-signed.html` | PDF signing flow |
| `shouldConsumeEbmsSentEvent()` | `ebms.sent` | EbmsSentEvent | `ebms-sent.html` | ebMS submission flow |
| `shouldConsumeSagaCompletedEvent()` | `saga.lifecycle.completed` | SagaCompletedEvent | `saga-completed.html` | Saga completion flow |

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

## Flow 8: Integration Test Execution (PdfGeneratedEvent)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  INTEGRATION TEST FLOW (PdfGeneratedEvent)                │
└─────────────────────────────────────────────────────────────────────────────┘

  KafkaConsumerIntegrationTest    TestKafkaProducer          Kafka (9093)
  ────────────────────────────    ─────────────────          ──────────────
       │                                │                          │
       │  1. Create PdfGeneratedEvent
       │     - invoiceId: "INV-{UUID}"
       │     - invoiceNumber: "T0001-{timestamp}"
       │     - documentId: "DOC-{UUID}"
       │     - documentUrl: "http://localhost:8084/api/v1/documents/{docId}"
       │     - fileSize: 125000 (125 KB)
       │     - xmlEmbedded: true
       │     - digitallySigned: false
       │     - correlationId: UUID
       │                                │                          │
       │  2. sendEvent("pdf.generated", invoiceId, event)
       ├──────────────────────────────►│                          │
       │                                │  3. Serialize to JSON    │
       │                                │     (ObjectMapper)       │
       │                                ├─────────────────────────►│
       │                                │                          │
       │                                │                  ┌───────┴───────┐
       │                                │                  │ PdfGenerated  │
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
       │                     │  pdf-generated        │
       │                     └──────────┬───────────┘
       │                                │
       │                                │  4. Unmarshal JSON to
       │                                │     PdfGeneratedEvent
       │                                │     (Jackson + JavaTimeModule)
       │                                │
       │                     ┌──────────┴───────────┐
       │                     │  Handle PDF          │
       │                     │  Generated Event     │
       │                     │  - Create template   │
       │                     │    variables         │
       │                     │    (includes fileSize│
       │                     │    formatting)       │
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
       │      - type == "PDF_GENERATED"
       │      - channel == "EMAIL"
       │      - status == "SENT"
       │      - template_name == "pdf-generated"
       │      - invoice_id, invoice_number, correlation_id match
       │      - subject contains "PDF Invoice Ready" and invoiceNumber
       │      - template_variables contains:
       │        * invoiceId, invoiceNumber, documentId, documentUrl
       │        * fileSize formatted (e.g., "125 KB")
       │        * xmlEmbedded (boolean as string)
       │        * digitallySigned (boolean as string)
```

### PdfGeneratedEvent Test Specifics

**Template Variables Created:**
- `invoiceId` - Invoice UUID
- `invoiceNumber` - Invoice number
- `documentId` - Document UUID
- `documentUrl` - Download URL for the PDF
- `fileSize` - Formatted human-readable size (e.g., "125 KB")
- `generatedAt` - Timestamp formatted from `event.getOccurredAt()`
- `xmlEmbedded` - Boolean indicating if XML is embedded in PDF
- `digitallySigned` - Boolean indicating if PDF is signed

**Special Validations:**
- Subject contains both "PDF Invoice Ready" and invoice number
- File size is formatted (contains "KB" or "MB" suffix)
- Boolean fields are serialized as strings ("true"/"false")
- Document URL and ID are properly propagated

**Camel Route Handler:**
`NotificationEventRoutes.handlePdfGenerated()` (lines 424-454)
- Calls `formatFileSize(event.getFileSize())` to convert bytes to human-readable format
- Sets `generatedAt` using `formatInstant(event.getOccurredAt())`
- Adds metadata entries for `documentUrl` and `documentId`

---

## Flow 9: Integration Test Execution (PdfSignedEvent)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  INTEGRATION TEST FLOW (PdfSignedEvent)                   │
└─────────────────────────────────────────────────────────────────────────────┘

  KafkaConsumerIntegrationTest    TestKafkaProducer          Kafka (9093)
  ────────────────────────────    ─────────────────          ──────────────
       │                                │                          │
       │  1. Create PdfSignedEvent
       │     - correlationId: UUID
       │     - invoiceId: "INV-{UUID}"
       │     - invoiceNumber: "T0001-{timestamp}"
       │     - documentType: "INVOICE"
       │     - signedDocumentId: "SIGNED-DOC-{UUID}"
       │     - signedPdfUrl: "http://localhost:8084/api/v1/documents/{docId}"
       │     - signedPdfSize: 130000 (130 KB)
       │     - transactionId: "TXN-{UUID}"
       │     - certificate: "MIIBIjANBgkqhkiG..."
       │     - signatureLevel: "PAdES-BASELINE-T"
       │     - signatureTimestamp: Instant.now()
       │                                │                          │
       │  2. sendEvent("pdf.signed", invoiceId, event)
       ├──────────────────────────────►│                          │
       │                                │  3. Serialize to JSON    │
       │                                │     (ObjectMapper)       │
       │                                ├─────────────────────────►│
       │                                │                          │
       │                                │                  ┌───────┴───────┐
       │                                │                  │ PdfSigned     │
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
       │                     │  pdf-signed           │
       │                     └──────────┬───────────┘
       │                                │
       │                                │  4. Unmarshal JSON to
       │                                │     PdfSignedEvent
       │                                │     (Jackson + JavaTimeModule)
       │                                │
       │                     ┌──────────┴───────────┐
       │                     │  Handle PDF Signed    │
       │                     │  Event                │
       │                     │  - Create template    │
       │                     │    variables          │
       │                     │    (includes fileSize │
       │                     │    and timestamp       │
       │                     │    formatting)        │
       │                     │  - Create Notification │
       │                     │    aggregate          │
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
       │      - type == "PDF_SIGNED"
       │      - channel == "EMAIL"
       │      - status == "SENT"
       │      - template_name == "pdf-signed"
       │      - invoice_id, invoice_number, correlation_id match
       │      - subject contains "PDF Invoice Signed" and invoiceNumber
       │      - template_variables contains:
       │        * invoiceId, invoiceNumber, documentType
       │        * signedDocumentId, signedPdfUrl
       │        * signedPdfSize formatted (e.g., "130 KB")
       │        * transactionId, signatureLevel
       │        * signatureTimestamp formatted
```

### PdfSignedEvent Test Specifics

**Template Variables Created:**
- `invoiceId` - Invoice UUID
- `invoiceNumber` - Invoice number
- `documentType` - Document type (INVOICE, TAX_INVOICE, etc.)
- `signedDocumentId` - Signed document UUID
- `signedPdfUrl` - Download URL for the signed PDF
- `signedPdfSize` - Formatted human-readable size (e.g., "130 KB")
- `transactionId` - Signing transaction ID
- `signatureLevel` - Signature level (e.g., "PAdES-BASELINE-T")
- `signatureTimestamp` - Timestamp formatted from `event.getSignatureTimestamp()`

**Special Validations:**
- Subject contains both "PDF Invoice Signed" and invoice number
- File size is formatted (contains "KB" or "MB" suffix)
- Document type is preserved (INVOICE, TAX_INVOICE, etc.)
- Transaction ID is properly propagated
- Signature level is included
- Signature timestamp is formatted (contains formatted date/time string)

**Camel Route Handler:**
`NotificationEventRoutes.handlePdfSigned()` (lines 464-498)
- Calls `formatFileSize(event.getSignedPdfSize())` to convert bytes to human-readable format
- Sets `signatureTimestamp` using `formatInstant(event.getSignatureTimestamp())`
- Adds metadata entries for `signedPdfUrl`, `signedDocumentId`, and `signatureLevel`

---

## Flow 10: Integration Test Execution (EbmsSentEvent)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  INTEGRATION TEST FLOW (EbmsSentEvent)                    │
└─────────────────────────────────────────────────────────────────────────────┘

  KafkaConsumerIntegrationTest    TestKafkaProducer          Kafka (9093)
  ────────────────────────────    ─────────────────          ──────────────
       │                                │                          │
       │  1. Create EbmsSentEvent
       │     - documentId: "DOC-{UUID}"
       │     - invoiceId: "INV-{UUID}"
       │     - invoiceNumber: "T0001-{timestamp}"
       │     - documentType: "INVOICE"
       │     - ebmsMessageId: "EBMS-{UUID}"
       │     - sentAt: Instant.now()
       │     - correlationId: UUID
       │                                │                          │
       │  2. sendEvent("ebms.sent", documentId, event)
       ├──────────────────────────────►│                          │
       │                                │  3. Serialize to JSON    │
       │                                │     (ObjectMapper)       │
       │                                ├─────────────────────────►│
       │                                │                          │
       │                                │                  ┌───────┴───────┐
       │                                │                  │ EbmsSent      │
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
       │                     │  ebms-sent            │
       │                     └──────────┬───────────┘
       │                                │
       │                                │  4. Unmarshal JSON to
       │                                │     EbmsSentEvent
       │                                │     (Jackson + JavaTimeModule)
       │                                │
       │                     ┌──────────┴───────────┐
       │                     │  Handle ebMS Sent    │
       │                     │  Event                │
       │                     │  - Create template    │
       │                     │    variables          │
       │                     │    (includes sentAt    │
       │                     │    formatting)        │
       │                     │  - Create Notification │
       │                     │    aggregate          │
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
       │  10. awaitNotificationByCorrelationId(correlationId)
       │      - Uses correlationId for lookup (invoiceId may be null)
       │      - Polls database every 1 second
       │      - Waits up to 2 minutes
       │      - Returns when status = SENT
       │
       │  11. Assertions:
       │      - type == "EBMS_SENT"
       │      - channel == "EMAIL"
       │      - status == "SENT"
       │      - template_name == "ebms-sent"
       │      - invoice_id, invoice_number, correlation_id match
       │      - subject contains "Document Submitted to TRD" and invoiceNumber
       │      - template_variables contains:
       │        * documentId, invoiceId, invoiceNumber, documentType
       │        * ebmsMessageId, sentAt (formatted), correlationId
```

### EbmsSentEvent Test Specifics

**Template Variables Created:**
- `documentId` - Document UUID
- `invoiceId` - Invoice UUID (or "N/A" if null)
- `invoiceNumber` - Invoice number (or "N/A" if null)
- `documentType` - Document type (INVOICE, TAX_INVOICE, etc.)
- `ebmsMessageId` - ebMS message ID from Thailand Revenue Department
- `sentAt` - Timestamp formatted from `event.getSentAt()`
- `correlationId` - Correlation ID for tracking

**Special Validations:**
- Subject contains both "Document Submitted to TRD" and invoice number (or document ID if invoice number is null)
- Document type is preserved (INVOICE, TAX_INVOICE, etc.)
- ebMS message ID is properly propagated
- Sent timestamp is formatted (contains formatted date/time string)
- **Uses `awaitNotificationByCorrelationId()`** for lookup since invoiceId may be null for some document types

**Camel Route Handler:**
`NotificationEventRoutes.handleEbmsSent()` (lines 572-606)
- Handles null values for invoiceId and invoiceNumber (uses "N/A" fallback)
- Sets `sentAt` using `formatInstant(event.getSentAt())`
- Uses invoiceNumber or documentId for subject display (whichever is available)
- Adds metadata entries for `ebmsMessageId` and `documentType`

---

## Flow 11: Integration Test Execution (SagaCompletedEvent)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                  INTEGRATION TEST FLOW (SagaCompletedEvent)               │
└─────────────────────────────────────────────────────────────────────────────┘

  KafkaConsumerIntegrationTest    TestKafkaProducer          Kafka (9093)
  ────────────────────────────    ─────────────────          ──────────────
       │                                │                          │
       │  1. Create SagaCompletedEvent
       │     - sagaId: "SAGA-{UUID}"
       │     - correlationId: UUID
       │     - documentType: "INVOICE"
       │     - documentId: "DOC-{UUID}"
       │     - invoiceNumber: "T0001-{timestamp}"
       │     - stepsExecuted: 7
       │     - startedAt: Instant.now().minusSeconds(120)
       │     - completedAt: Instant.now()
       │     - durationMs: 120000L (2 minutes)
       │                                │                          │
       │  2. sendEvent("saga.lifecycle.completed", documentId, event)
       ├──────────────────────────────►│                          │
       │                                │  3. Serialize to JSON    │
       │                                │     (ObjectMapper)       │
       │                                ├─────────────────────────►│
       │                                │                          │
       │                                │                  ┌───────┴───────┐
       │                                │                  │ SagaCompleted │
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
       │                     │  saga-completed       │
       │                     └──────────┬───────────┘
       │                                │
       │                                │  4. Unmarshal JSON to
       │                                │     SagaCompletedEvent
       │                                │     (Jackson + JavaTimeModule)
       │                                │
       │                     ┌──────────┴───────────┐
       │                     │  Handle Saga          │
       │                     │  Completed Event      │
       │                     │  - Create template    │
       │                     │    variables          │
       │                     │    (includes duration │
       │                     │    formatting)        │
       │                     │  - Create Notification │
       │                     │    aggregate          │
       │                     │  - Set invoiceId =     │
       │                     │    documentId          │
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
       │  10. awaitNotificationByInvoiceId(documentId)
       │      - Handler sets invoiceId to documentId
       │      - Polls database every 1 second
       │      - Waits up to 2 minutes
       │      - Returns when status = SENT
       │
       │  11. Assertions:
       │      - type == "SAGA_COMPLETED"
       │      - channel == "EMAIL"
       │      - status == "SENT"
       │      - template_name == "saga-completed"
       │      - invoice_id == documentId (handler sets this)
       │      - invoice_number, correlation_id match
       │      - subject contains "Saga Completed" and invoiceNumber
       │      - template_variables contains:
       │        * sagaId, documentId, invoiceNumber, documentType
       │        * stepsExecuted, durationMs, durationSec (formatted)
       │        * completedAt (formatted)
```

### SagaCompletedEvent Test Specifics

**Template Variables Created:**
- `sagaId` - Saga orchestration UUID
- `documentId` - Document UUID
- `invoiceNumber` - Invoice number (or "N/A" if null)
- `documentType` - Document type (INVOICE, TAX_INVOICE, etc.)
- `stepsExecuted` - Number of saga steps executed
- `durationMs` - Duration in milliseconds
- `durationSec` - Duration formatted in seconds (e.g., "120.00")
- `completedAt` - Completion timestamp formatted from `event.getCompletedAt()`

**Special Validations:**
- Subject contains both "Saga Completed" and invoice number (or document ID if invoice number is null)
- Document type is preserved (INVOICE, TAX_INVOICE, etc.)
- Saga ID is properly propagated
- Steps executed count is included
- Duration is formatted in seconds (contains "durationSec" key with formatted value)
- Completion timestamp is formatted (contains formatted date/time string)
- **Uses `awaitNotificationByInvoiceId(documentId)`** for lookup since handler sets `invoiceId = documentId`

**Camel Route Handler:**
`NotificationEventRoutes.handleSagaCompleted()` (lines 636-666)
- Handles null invoiceNumber (uses "N/A" fallback in template variables)
- Sets `invoiceId` to `documentId` for tracking purposes
- Sets `durationSec` using `String.format("%.2f", event.getDurationMs() / 1000.0)`
- Sets `completedAt` using `formatInstant(event.getCompletedAt())`
- Uses invoiceNumber or documentId for subject display (whichever is available)
- Adds metadata entry for `sagaId`

---
