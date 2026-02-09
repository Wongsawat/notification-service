# Notification Service

Microservice for sending notifications via multiple channels (email, SMS, webhooks) in response to invoice processing events.

## Overview

The Notification Service:

- ✅ **Listens** to Kafka events from invoice processing pipeline via Apache Camel routes
- ✅ **Observes** saga orchestrator lifecycle events (started, completed, failed)
- ✅ **Sends** notifications via email, webhooks (SMS support planned)
- ✅ **Renders** HTML email templates using Thymeleaf
- ✅ **Tracks** notification status and delivery
- ✅ **Retries** failed notifications with exponential backoff
- ✅ **Provides** REST API for manual notification triggering
- ✅ **Supports** correlation IDs for distributed tracing
- ✅ **Implements** transactional outbox pattern for reliable event publishing
- ✅ **Standardizes** event structure via `IntegrationEvent` base class from saga-commons

## Architecture

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Database | PostgreSQL 16 |
| Messaging | Apache Kafka via Apache Camel 4.14.4 |
| Saga Pattern | saga-commons (Outbox + CDC + IntegrationEvent base class) |
| CDC | Debezium for outbox pattern |
| Email | Spring Mail (SMTP) |
| Templates | Thymeleaf |
| HTTP Client | WebFlux WebClient |
| Service Discovery | Netflix Eureka |

### Domain Model

**Aggregate Root:**
- `Notification` - Notification with state machine (PENDING → SENDING → SENT/FAILED)

**Value Objects:**
- `NotificationType` - INVOICE_RECEIVED, INVOICE_PROCESSED, PDF_GENERATED, etc.
- `NotificationChannel` - EMAIL, SMS, WEBHOOK, IN_APP
- `NotificationStatus` - PENDING, SENDING, SENT, FAILED, RETRYING

**Domain Services:**
- `NotificationSender` - Abstract sender interface
- `EmailNotificationSender` - Email implementation
- `WebhookNotificationSender` - Webhook implementation

### Event Standardization

All event DTOs extend `IntegrationEvent` from saga-commons, providing:
- **eventId** (UUID) - Unique event identifier
- **occurredAt** (Instant) - Event timestamp
- **eventType** (String) - Event type name
- **version** (int) - Event schema version

This ensures consistent event structure across all microservices in the invoice processing ecosystem.

## Notification Flow

```
1. Invoice event published to Kafka (invoice.received, invoice.processed, pdf.generated)
   ↓
2. Camel route consumes event (via NotificationEventRoutes)
   ↓
3. Event unmarshalled to DTO (extends IntegrationEvent)
   ↓
4. Create Notification aggregate with template variables
   ↓
5. NotificationService orchestrates sending
   ↓
6. TemplateEngine renders email HTML (if template-based)
   ↓
7. NotificationSender sends via appropriate channel
   ↓
8. Update notification status (SENT or FAILED)
   ↓
9. (If failed) Scheduled retry task attempts redelivery
```

## Supported Notification Types

### Invoice Processed
- **Trigger**: Invoice processing completed
- **Template**: `invoice-processed.html`
- **Channel**: Email
- **Variables**: invoiceId, invoiceNumber, totalAmount, currency, processedAt (from occurredAt)

### Tax Invoice Processed
- **Trigger**: Tax invoice processing completed
- **Template**: `taxinvoice-processed.html`
- **Channel**: Email
- **Variables**: invoiceId, invoiceNumber, total, currency, processedAt (from occurredAt)

### PDF Generated
- **Trigger**: PDF generation completed
- **Template**: `pdf-generated.html`
- **Channel**: Email
- **Variables**: invoiceId, invoiceNumber, documentId, documentUrl, fileSize, generatedAt (from occurredAt), xmlEmbedded, digitallySigned

### PDF Signed
- **Trigger**: PDF signing completed
- **Template**: `pdf-signed.html`
- **Channel**: Email
- **Variables**: invoiceId, invoiceNumber, documentType, signedDocumentId, signedPdfUrl, signedPdfSize, transactionId, signatureLevel, signatureTimestamp

### ebMS Sent
- **Trigger**: Document submitted to Thailand Revenue Department
- **Template**: `ebms-sent.html`
- **Channel**: Email
- **Variables**: documentId, invoiceId, invoiceNumber, documentType, ebmsMessageId, sentAt, correlationId

### Saga Lifecycle Notifications

#### Saga Completed
- **Trigger**: Saga orchestration completed successfully
- **Template**: `saga-completed.html`
- **Channel**: Email
- **Variables**: sagaId, documentId, invoiceNumber, documentType, stepsExecuted, durationSec, completedAt
- **Subject**: "Saga Completed: {invoiceNumber}"
- **Theme**: Green success theme

#### Saga Failed
- **Trigger**: Saga orchestration failed
- **Template**: `saga-failed.html`
- **Channel**: Email
- **Variables**: sagaId, documentId, invoiceNumber, documentType, failedStep, errorMessage, retryCount, compensationInitiated, failedAt
- **Subject**: "URGENT: Saga Failed - {invoiceNumber}"
- **Theme**: Red alert theme with compensation status

#### Saga Started / Step Completed
- **Trigger**: Saga started or step completed
- **Action**: Logged only (no email notification sent)
- **Purpose**: Monitoring and audit trail

## REST API

### Send Notification
```bash
POST /api/v1/notifications
Content-Type: application/json

{
  "type": "INVOICE_PROCESSED",
  "channel": "EMAIL",
  "recipient": "user@example.com",
  "subject": "Invoice Processed",
  "templateName": "invoice-processed",
  "templateVariables": {
    "invoiceNumber": "INV-2026-001",
    "invoiceId": "uuid",
    "processedAt": "2026-02-06 10:30:00"
  },
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2026-001",
  "correlationId": "trace-id"
}

Response: 200 OK
{
  "notificationId": "uuid",
  "status": "SENT",
  "createdAt": "2026-02-06T10:30:00",
  "sentAt": "2026-02-06T10:30:05"
}
```

### Get Notification by ID
```bash
GET /api/v1/notifications/{id}

Response: 200 OK
{
  "id": "uuid",
  "type": "INVOICE_PROCESSED",
  "channel": "EMAIL",
  "status": "SENT",
  "recipient": "user@example.com",
  "subject": "Invoice Processed",
  "createdAt": "2026-02-06T10:30:00",
  "sentAt": "2026-02-06T10:30:05"
}
```

### Get Notifications by Invoice
```bash
GET /api/v1/notifications/invoice/{invoiceId}

Response: 200 OK
[
  {
    "id": "uuid1",
    "type": "INVOICE_PROCESSED",
    "status": "SENT",
    "sentAt": "2026-02-06T10:30:05"
  },
  {
    "id": "uuid2",
    "type": "PDF_GENERATED",
    "status": "SENT",
    "sentAt": "2026-02-06T10:35:10"
  }
]
```

### Get Notification Statistics
```bash
GET /api/v1/notifications/statistics

Response: 200 OK
{
  "pending": 5,
  "sending": 2,
  "sent": 1523,
  "failed": 12,
  "retrying": 3
}
```

### Retry Failed Notification
```bash
POST /api/v1/notifications/{id}/retry

Response: 200 OK
{
  "message": "Retry scheduled"
}
```

## Kafka Integration

**Important**: This service uses **Apache Camel 4.14.4** for Kafka consumption, not Spring Kafka's `@KafkaListener`.

### Apache Camel Routes

All Kafka consumption is defined in `NotificationEventRoutes.java` with **16 total routes**:

**Processing Events (12 routes):**
- `notification-invoice-processed` → `invoice.processed`
- `notification-taxinvoice-processed` → `taxinvoice.processed`
- `notification-pdf-generated` → `pdf.generated`
- `notification-pdf-signed` → `pdf.signed`
- `notification-ebms-sent` → `ebms.sent`
- `notification-document-counting` → `document.received` (logging)
- `notification-tax-invoice-received` → `document.received.tax-invoice` (logging)
- `notification-invoice-received` → `document.received.invoice` (logging)
- `notification-receipt-received` → `document.received.receipt` (logging)
- `notification-debit-credit-note-received` → `document.received.debit-credit-note` (logging)
- `notification-cancellation-received` → `document.received.cancellation` (logging)
- `notification-abbreviated-received` → `document.received.abbreviated` (logging)

**Saga Lifecycle Events (4 routes):**
- `notification-saga-started` → `saga.lifecycle.started` (logging only)
- `notification-saga-step-completed` → `saga.lifecycle.step-completed` (logging only)
- `notification-saga-completed` → `saga.lifecycle.completed` (creates email)
- `notification-saga-failed` → `saga.lifecycle.failed` (creates urgent email)

### Consumed Topics

| Topic | Event | Route | Action |
|-------|-------|-------|--------|
| `invoice.processed` | InvoiceProcessedEvent | notification-invoice-processed | Email notification |
| `taxinvoice.processed` | TaxInvoiceProcessedEvent | notification-taxinvoice-processed | Email notification |
| `pdf.generated` | PdfGeneratedEvent | notification-pdf-generated | Email notification |
| `pdf.signed` | PdfSignedEvent | notification-pdf-signed | Email notification |
| `ebms.sent` | EbmsSentEvent | notification-ebms-sent | Email notification |
| `saga.lifecycle.completed` | SagaCompletedEvent | notification-saga-completed | Email notification |
| `saga.lifecycle.failed` | SagaFailedEvent | notification-saga-failed | Urgent email notification |
| `saga.lifecycle.started` | SagaStartedEvent | notification-saga-started | Logging only |
| `saga.lifecycle.step-completed` | SagaStepCompletedEvent | notification-saga-step-completed | Logging only |

### Event Schemas

**InvoiceProcessedEvent** (extends IntegrationEvent)
```json
{
  "eventId": "uuid",
  "occurredAt": "2026-02-06T10:35:00Z",
  "eventType": "InvoiceProcessedEvent",
  "version": 1,
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2026-001",
  "totalAmount": 1500.00,
  "currency": "THB",
  "correlationId": "trace-id"
}
```

**TaxInvoiceProcessedEvent** (extends IntegrationEvent)
```json
{
  "eventId": "uuid",
  "occurredAt": "2026-02-06T10:35:00Z",
  "eventType": "TaxInvoiceProcessedEvent",
  "version": 1,
  "invoiceId": "uuid",
  "invoiceNumber": "TAX-2026-001",
  "total": 1500.00,
  "currency": "THB",
  "correlationId": "trace-id"
}
```

**PdfGeneratedEvent** (extends IntegrationEvent)
```json
{
  "eventId": "uuid",
  "occurredAt": "2026-02-06T10:40:00Z",
  "eventType": "PdfGeneratedEvent",
  "version": 1,
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2026-001",
  "documentId": "doc-uuid",
  "documentUrl": "http://storage:8084/api/v1/documents/doc-uuid",
  "fileSize": 125000,
  "xmlEmbedded": true,
  "digitallySigned": false,
  "correlationId": "trace-id"
}
```

**PdfSignedEvent** (extends IntegrationEvent)
```json
{
  "eventId": "uuid",
  "occurredAt": "2026-02-06T10:45:00Z",
  "eventType": "PdfSignedEvent",
  "version": 1,
  "correlationId": "trace-id",
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2026-001",
  "documentType": "INVOICE",
  "signedDocumentId": "doc-signed-uuid",
  "signedPdfUrl": "http://storage:8084/api/v1/documents/doc-signed-uuid",
  "signedPdfSize": 130000,
  "transactionId": "txn-uuid",
  "certificate": "base64-cert",
  "signatureLevel": "PAdES-BASELINE-T",
  "signatureTimestamp": "2026-02-06T10:45:00Z"
}
```

**EbmsSentEvent** (extends IntegrationEvent)
```json
{
  "eventId": "uuid",
  "occurredAt": "2026-02-06T10:50:00Z",
  "eventType": "EbmsSentEvent",
  "version": 1,
  "documentId": "doc-uuid",
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2026-001",
  "documentType": "TAX_INVOICE",
  "ebmsMessageId": "ebms-msg-id",
  "sentAt": "2026-02-06T10:50:00Z",
  "correlationId": "trace-id"
}
```

**SagaCompletedEvent** (from orchestrator-service, extends IntegrationEvent)
```json
{
  "eventId": "uuid",
  "occurredAt": "2026-02-06T14:00:00Z",
  "eventType": "SagaCompletedEvent",
  "version": 1,
  "sagaId": "saga-uuid",
  "correlationId": "trace-id",
  "documentType": "TAX_INVOICE",
  "documentId": "doc-uuid",
  "invoiceNumber": "INV-2026-001",
  "stepsExecuted": 6,
  "startedAt": "2026-02-06T13:59:30Z",
  "completedAt": "2026-02-06T14:00:00Z",
  "durationMs": 30000
}
```

**SagaFailedEvent** (from orchestrator-service, extends IntegrationEvent)
```json
{
  "eventId": "uuid",
  "occurredAt": "2026-02-06T14:00:00Z",
  "eventType": "SagaFailedEvent",
  "version": 1,
  "sagaId": "saga-uuid",
  "correlationId": "trace-id",
  "documentType": "TAX_INVOICE",
  "documentId": "doc-uuid",
  "invoiceNumber": "INV-2026-001",
  "failedStep": "SIGN_XML",
  "errorMessage": "CSC API connection timeout",
  "retryCount": 3,
  "compensationInitiated": true,
  "startedAt": "2026-02-06T13:59:30Z",
  "failedAt": "2026-02-06T14:00:00Z",
  "durationMs": 30000
}
```

## Email Templates

Templates are located in `src/main/resources/templates/` and use Thymeleaf syntax.

### Template Variables

**invoice-processed.html**
- `invoiceNumber` - Invoice number
- `invoiceId` - Invoice UUID
- `totalAmount` - Formatted total (e.g., "1,500.00")
- `currency` - Currency code (e.g., "THB")
- `processedAt` - Processing timestamp (from event's occurredAt)

**taxinvoice-processed.html**
- `invoiceNumber` - Tax invoice number
- `invoiceId` - Tax invoice UUID
- `total` - Formatted total
- `currency` - Currency code
- `processedAt` - Processing timestamp (from event's occurredAt)

**pdf-generated.html**
- `invoiceNumber` - Invoice number
- `documentId` - Document UUID
- `documentUrl` - Download URL
- `fileSize` - Human-readable size (e.g., "125 KB")
- `generatedAt` - Generation timestamp (from event's occurredAt)
- `xmlEmbedded` - Whether XML is embedded in PDF
- `digitallySigned` - Whether PDF is signed

**pdf-signed.html**
- `invoiceNumber` - Invoice number
- `documentType` - Document type (INVOICE, TAX_INVOICE, etc.)
- `signedDocumentId` - Signed document UUID
- `signedPdfUrl` - Download URL for signed PDF
- `signedPdfSize` - File size
- `transactionId` - Signing transaction ID
- `signatureLevel` - Signature level (e.g., "PAdES-BASELINE-T")
- `signatureTimestamp` - Signature timestamp

**ebms-sent.html**
- `documentId` - Document UUID
- `invoiceId` - Invoice UUID (nullable)
- `invoiceNumber` - Invoice number (nullable)
- `documentType` - Document type
- `ebmsMessageId` - ebMS message ID
- `sentAt` - Submission timestamp
- `correlationId` - Correlation ID

**saga-completed.html** (green success theme)
- `sagaId` - Saga orchestration ID
- `documentId` - Document UUID
- `invoiceNumber` - Invoice number
- `documentType` - Document type (TAX_INVOICE, INVOICE, etc.)
- `stepsExecuted` - Number of steps completed
- `durationSec` - Duration in seconds (formatted)
- `completedAt` - Completion timestamp

**saga-failed.html** (red alert theme)
- `sagaId` - Saga orchestration ID
- `documentId` - Document UUID
- `invoiceNumber` - Invoice number
- `documentType` - Document type
- `failedStep` - Step that failed (e.g., "SIGN_XML")
- `errorMessage` - Error details
- `retryCount` - Number of retry attempts
- `compensationInitiated` - Whether compensation started (boolean)
- `failedAt` - Failure timestamp

### Creating Custom Templates

1. Create HTML file in `src/main/resources/templates/`
2. Use Thymeleaf syntax for variables: `th:text="${variableName}"`
3. Reference in notification: `templateName = "your-template"`

Example:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
  <h1>Hello, <span th:text="${recipientName}">User</span>!</h1>
  <p th:text="${message}">Your message here</p>
</body>
</html>
```

## Database Schema

### notifications Table

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| type | VARCHAR(50) | Notification type |
| channel | VARCHAR(20) | Delivery channel |
| status | VARCHAR(20) | Current status |
| recipient | VARCHAR(500) | Email/phone/webhook URL |
| subject | VARCHAR(500) | Email subject |
| body | TEXT | Email body (if not template-based) |
| metadata | TEXT | Additional metadata (JSON via AttributeConverter) |
| template_name | VARCHAR(100) | Template file name |
| template_variables | TEXT | Template variable values (JSON via AttributeConverter) |
| invoice_id | VARCHAR(100) | Related invoice ID |
| invoice_number | VARCHAR(100) | Related invoice number |
| correlation_id | VARCHAR(100) | Distributed trace ID |
| created_at | TIMESTAMP | Creation time |
| sent_at | TIMESTAMP | Successful send time |
| failed_at | TIMESTAMP | Failure time |
| retry_count | INTEGER | Number of retries |
| error_message | TEXT | Error details |

### Indexes
- `idx_notifications_status` - Status queries
- `idx_notifications_invoice_id` - Invoice lookups
- `idx_notifications_recipient` - Recipient searches
- `idx_notifications_created_at` - Time-based queries
- `idx_notifications_failed_retry` - Failed notification retries

### outbox_events Table (Saga Pattern)

Implements the Transactional Outbox Pattern for reliable event publishing via Debezium CDC.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| aggregate_type | VARCHAR(100) | Business entity type (e.g., "Notification") |
| aggregate_id | VARCHAR(100) | Business entity ID |
| event_type | VARCHAR(100) | Event class name |
| payload | TEXT | JSON serialized event |
| topic | VARCHAR(255) | Target Kafka topic (for CDC routing) |
| partition_key | VARCHAR(255) | Kafka partition key |
| headers | TEXT | Additional Kafka headers (JSON) |
| status | VARCHAR(20) | PENDING, PUBLISHED, FAILED |
| retry_count | INTEGER | Publication retry attempts |
| error_message | VARCHAR(1000) | Publication error details |
| created_at | TIMESTAMP | Event creation time |
| published_at | TIMESTAMP | Successful publication time |

### Outbox Indexes
- `idx_outbox_status` - Status queries
- `idx_outbox_created` - Chronological processing
- `idx_outbox_debezium` - Partial index (status='PENDING') for CDC optimization
- `idx_outbox_aggregate` - Aggregate queries

### Migrations
- **V1**: `create_notifications_table.sql` - Main notifications table
- **V2**: `create_outbox_events_table.sql` - Outbox pattern for saga integration

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `notification_db` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `KAFKA_BROKERS` | Kafka servers | `localhost:9092` |
| `MAIL_HOST` | SMTP server host | `smtp.gmail.com` |
| `MAIL_PORT` | SMTP server port | `587` |
| `MAIL_USERNAME` | SMTP username | - |
| `MAIL_PASSWORD` | SMTP password | - |
| `DEFAULT_RECIPIENT` | Default email recipient | `admin@example.com` |
| `NOTIFICATION_ENABLED` | Enable/disable notifications | `true` |
| `MAX_RETRIES` | Maximum retry attempts | `3` |

### Email Configuration

For Gmail:
```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: your-app-password  # Use App Password, not regular password
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

For SendGrid:
```yaml
spring:
  mail:
    host: smtp.sendgrid.net
    port: 587
    username: apikey
    password: your-sendgrid-api-key
```

For AWS SES:
```yaml
spring:
  mail:
    host: email-smtp.us-east-1.amazonaws.com
    port: 587
    username: your-smtp-username
    password: your-smtp-password
```

## Running the Service

### Prerequisites
1. PostgreSQL 16+ running with `notification_db` database
2. Kafka broker running (port 9092 or 9093 for Docker test environment)
3. SMTP server credentials (Gmail, SendGrid, AWS SES, etc.)
4. **saga-commons library** installed: `cd ../../../saga-commons && mvn clean install`
5. **Debezium Connect** running (port 8083) for CDC-based event publishing (optional but recommended)

### Build
```bash
# Install saga-commons dependency first
cd ../../../saga-commons
mvn clean install

# Build notification-service
cd ../invoice-microservices/services/notification-service
mvn clean package
```

### Run Locally
```bash
export DB_HOST=localhost
export DB_NAME=notification_db
export KAFKA_BROKERS=localhost:9092
export MAIL_HOST=smtp.gmail.com
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-app-password
export DEFAULT_RECIPIENT=admin@example.com

mvn spring-boot:run
```

### Run with Docker
```bash
docker build -t notification-service:latest .

docker run -p 8085:8085 \
  -e DB_HOST=postgres \
  -e KAFKA_BROKERS=kafka:29092 \
  -e MAIL_HOST=smtp.gmail.com \
  -e MAIL_USERNAME=your-email@gmail.com \
  -e MAIL_PASSWORD=your-app-password \
  -e DEFAULT_RECIPIENT=admin@example.com \
  notification-service:latest
```

## Retry Mechanism

### Automatic Retry
- **Scheduled Task**: Runs every 5 minutes (configurable)
- **Max Retries**: 3 attempts (configurable)
- **Retry Logic**: Exponential backoff (implicit via scheduled delay)

### Manual Retry
Use REST API:
```bash
POST /api/v1/notifications/{id}/retry
```

### Retry Flow
```
1. Notification fails → Status = FAILED, failedAt set, errorMessage stored
   ↓
2. Scheduled task findFailedNotifications(maxRetries=3)
   ↓
3. For each failed notification with retryCount < 3:
   - prepareRetry() → Status = RETRYING, retryCount++
   - sendNotificationAsync()
   ↓
4. If success → Status = SENT
   If failure → Status = FAILED (can retry again until retryCount >= maxRetries)
```

## Webhook Notifications

### Configuration
Set notification channel to `WEBHOOK` and provide webhook URL as recipient:

```json
{
  "type": "INVOICE_PROCESSED",
  "channel": "WEBHOOK",
  "recipient": "https://your-api.com/webhooks/invoice",
  "templateVariables": {
    "invoiceNumber": "INV-2026-001",
    "totalAmount": "1,500.00"
  }
}
```

### Webhook Payload
```json
{
  "notificationId": "uuid",
  "type": "INVOICE_PROCESSED",
  "subject": "Invoice Processed: INV-2026-001",
  "body": null,
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2026-001",
  "correlationId": "trace-id",
  "timestamp": "2026-02-06T10:35:00",
  "metadata": {},
  "data": {
    "invoiceNumber": "INV-2026-001",
    "totalAmount": "1,500.00",
    "currency": "THB"
  }
}
```

### Webhook Timeouts
- **Timeout**: 30 seconds
- **Retries**: 3 attempts (via scheduled retry task)
- **Expected Response**: HTTP 2xx (any 2xx status considered success)

## Monitoring

### Actuator Endpoints
- `/actuator/health` - Service health status
- `/actuator/info` - Service information
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus metrics

### Key Metrics
- `notification.sent.total` - Total notifications sent
- `notification.failed.total` - Total notification failures
- `notification.retry.total` - Total retry attempts
- `notification.processing.time` - Notification processing time

### Health Check
```bash
curl http://localhost:8085/actuator/health
```

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests

Integration tests verify end-to-end Kafka event consumption using Apache Camel routes with Testcontainers.

**Prerequisites:**
- Start test containers (PostgreSQL on port 5433, Kafka on port 9093):
```bash
cd ../../../invoice-microservices
./scripts/test-containers-start.sh
```

**Run Integration Tests:**
```bash
# All integration tests
mvn test -Pintegration -Dtest=KafkaConsumerIntegrationTest

# Specific test method
mvn test -Pintegration -Dtest=KafkaConsumerIntegrationTest#shouldConsumeTaxInvoiceProcessedEvent
```

**Integration Test Coverage:**
- `shouldConsumeInvoiceProcessedEvent()` - Verifies `invoice.processed` topic consumption and notification creation
- `shouldConsumeTaxInvoiceProcessedEvent()` - Verifies `taxinvoice.processed` topic consumption and notification creation
- `shouldConsumePdfGeneratedEvent()` - Verifies `pdf.generated` topic consumption and notification creation
- `shouldConsumePdfSignedEvent()` - Verifies `pdf.signed` topic consumption and notification creation
- `shouldConsumeEbmsSentEvent()` - Verifies `ebms.sent` topic consumption and notification creation
- `shouldConsumeSagaCompletedEvent()` - Verifies `saga.lifecycle.completed` topic consumption and notification creation

All tests validate:
- Event unmarshalling from Kafka JSON
- Notification aggregate creation with correct type, template, and variables
- Async processing completion (status reaches SENT)
- Database persistence of all notification fields

### Manual Testing - Send Test Email
```bash
curl -X POST http://localhost:8085/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "type": "INVOICE_PROCESSED",
    "channel": "EMAIL",
    "recipient": "test@example.com",
    "subject": "Test Notification",
    "templateName": "invoice-processed",
    "templateVariables": {
      "invoiceNumber": "TEST-001",
      "invoiceId": "test-uuid",
      "processedAt": "2026-02-06 10:30:00"
    }
  }'
```

## Security Considerations

### Email Security
1. **STARTTLS Required**: Enforces encrypted SMTP connections
2. **App Passwords**: Use OAuth2 or app-specific passwords, not account passwords
3. **Credential Encryption**: Store SMTP credentials in secrets manager (AWS Secrets Manager, Vault)

### Webhook Security
1. **HTTPS Only**: Enforce HTTPS for webhook URLs (recommended)
2. **Signature Verification**: Add HMAC signatures to webhook payloads (future)
3. **IP Whitelisting**: Restrict webhook sources (application-level)

### Data Protection
1. **PII in Logs**: Avoid logging recipient email addresses at INFO level
2. **Error Messages**: Don't expose sensitive data in error messages
3. **Database Encryption**: Encrypt notification_db at rest

## Troubleshooting

### Email Not Sending

**Check SMTP Configuration:**
```bash
# Test SMTP connection
telnet smtp.gmail.com 587

# Check logs
docker logs notification-service | grep "mail"
```

**Common Issues:**
- Gmail: Enable "Less secure app access" or use App Password
- Office 365: Use `smtp.office365.com:587`
- AWS SES: Verify sender email address in SES console

### Notifications Stuck in PENDING

**Check scheduled tasks:**
```bash
# Verify scheduled tasks are enabled
grep "EnableScheduling" src/main/java/com/wpanther/notification/NotificationServiceApplication.java

# Check task configuration
grep "processing-interval" src/main/resources/application.yml
```

**Manual trigger:**
```bash
# Trigger pending notifications manually
curl -X POST http://localhost:8085/api/v1/notifications/{id}/retry
```

### Kafka Events Not Consumed

**Check Kafka connectivity:**
```bash
# List consumer groups
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list

# Check lag
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group notification-service --describe
```

**Verify topic subscription:**
```bash
# Check application.yml
grep "invoice.processed" src/main/resources/application.yml
```

### Saga Events Not Processing

**Jackson Instant Deserialization Error:**
```
Java 8 date/time type `java.time.Instant` not supported by default
```

**Solution**: Ensure `jackson-datatype-jsr310` dependency is present in `pom.xml`.

**Check Camel Routes:**
```bash
# Verify all 16 routes are running
curl http://localhost:8085/actuator/camel/routes | jq '.[] | select(.routeId | startswith("notification-saga"))'

# Expected: 4 saga routes in "Started" state
```

**Debezium Connector Issues:**
```bash
# Check connector status
curl http://localhost:8083/connectors/outbox-connector-notification/status | jq

# Expected: connector.state = "RUNNING", tasks[0].state = "RUNNING"

# Verify PostgreSQL replication slot
psql -h localhost -p 5433 -U postgres -d notification_db \
  -c "SELECT slot_name, active FROM pg_replication_slots WHERE slot_name='notification_outbox_slot';"

# Expected: active = t
```

**Consumer Group Not Assigned:**
```bash
# Check consumer group assignments
docker exec test-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9093 \
  --group notification-service \
  --describe | grep saga.lifecycle

# Expected: Consumer assigned to all 3 partitions of each saga topic
```

## Future Enhancements

### Planned Features
1. **SMS Support** - Integrate with Twilio or AWS SNS
2. **Push Notifications** - Mobile app push notifications
3. **Webhook Signatures** - HMAC-SHA256 signatures for webhook security
4. **Batch Notifications** - Send multiple notifications in single API call
5. **Template Editor** - Web UI for editing email templates
6. **A/B Testing** - Test different email templates
7. **Unsubscribe Management** - Track and respect unsubscribe preferences
8. **Rich Media** - Attach PDFs, images to emails
9. **Delivery Reports** - Track email open rates, click rates
10. **Multi-language Support** - Localized templates

### Recommended Improvements
1. **Rate Limiting** - Prevent email flooding
2. **Circuit Breaker** - Protect against SMTP server failures
3. **Event Sourcing** - Full audit trail of notification lifecycle
4. **GraphQL API** - Alternative to REST for flexible queries
5. **WebSocket Support** - Real-time notification status updates

## Project Structure

```
src/main/java/com/wpanther/notification/
├── NotificationServiceApplication.java
├── domain/
│   ├── model/              # Notification, NotificationType, NotificationChannel, NotificationStatus
│   ├── repository/         # NotificationRepository interface
│   └── service/            # NotificationSender interface
├── application/
│   ├── controller/         # NotificationController (REST API)
│   └── service/            # NotificationService (orchestration)
└── infrastructure/
    ├── persistence/        # JPA entities, repositories
    ├── notification/       # Email, webhook senders, TemplateEngine
    ├── messaging/          # Kafka routes, event DTOs (all extend IntegrationEvent)
    └── config/             # Kafka, WebClient configuration

src/main/resources/
├── templates/              # Thymeleaf email templates
├── db/migration/           # Flyway SQL migrations
└── application.yml         # Configuration
```

## License

MIT License

## Contact

Maintained by wpanther (rabbit_roger@yahoo.com)
