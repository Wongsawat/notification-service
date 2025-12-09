# Program Flow

This document describes the notification processing flows in the Notification Service.

## Overview

The service processes notifications through two entry points:
1. **Kafka Events** - Automatic notifications triggered by invoice processing events
2. **REST API** - Manual notification requests

## Flow 1: Kafka Event-Driven Notifications

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         KAFKA EVENT FLOW                                     │
└─────────────────────────────────────────────────────────────────────────────┘

  Kafka Topics                    InvoiceEventListener              NotificationService
  ────────────                    ────────────────────              ───────────────────
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

| Kafka Topic | Event Class | Template | Subject Pattern |
|-------------|-------------|----------|-----------------|
| `invoice.received` | InvoiceReceivedEvent | invoice-received.html | "Invoice Received: {invoiceNumber}" |
| `invoice.processed` | InvoiceProcessedEvent | invoice-processed.html | "Invoice Processed: {invoiceNumber}" |
| `pdf.generated` | PdfGeneratedEvent | pdf-generated.html | "PDF Invoice Ready: {invoiceNumber}" |

### Template Variables by Event Type

**InvoiceReceivedEvent:**
- `invoiceId`, `invoiceNumber`, `receivedAt`, `source`

**InvoiceProcessedEvent:**
- `invoiceId`, `invoiceNumber`, `totalAmount`, `currency`, `processedAt`

**PdfGeneratedEvent:**
- `invoiceId`, `invoiceNumber`, `documentId`, `documentUrl`, `fileSize`, `generatedAt`, `xmlEmbedded`, `digitallySigned`

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
│  │                           MESSAGING                                      │ │
│  │  InvoiceEventListener ◄── Kafka Consumer ◄── Kafka Topics               │ │
│  │  - invoice.received                                                      │ │
│  │  - invoice.processed                                                     │ │
│  │  - pdf.generated                                                         │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
```

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
