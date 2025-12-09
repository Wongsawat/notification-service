# Notification Service

Microservice for sending notifications via multiple channels (email, SMS, webhooks) in response to invoice processing events.

## Overview

The Notification Service:

- ✅ **Listens** to Kafka events from invoice processing pipeline
- ✅ **Sends** notifications via email, webhooks (SMS support planned)
- ✅ **Renders** HTML email templates using Thymeleaf
- ✅ **Tracks** notification status and delivery
- ✅ **Retries** failed notifications with exponential backoff
- ✅ **Provides** REST API for manual notification triggering
- ✅ **Supports** correlation IDs for distributed tracing

## Architecture

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Database | PostgreSQL 16 |
| Messaging | Apache Kafka |
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

## Notification Flow

```
1. Invoice event published to Kafka (invoice.received, invoice.processed, pdf.generated)
   ↓
2. InvoiceEventListener consumes event
   ↓
3. Create Notification aggregate with template variables
   ↓
4. NotificationService orchestrates sending
   ↓
5. TemplateEngine renders email HTML (if template-based)
   ↓
6. NotificationSender sends via appropriate channel
   ↓
7. Update notification status (SENT or FAILED)
   ↓
8. (If failed) Scheduled retry task attempts redelivery
```

## Supported Notification Types

### Invoice Received
- **Trigger**: Invoice intake completed
- **Template**: `invoice-received.html`
- **Channel**: Email
- **Variables**: invoiceId, invoiceNumber, receivedAt, source

### Invoice Processed
- **Trigger**: Invoice processing completed
- **Template**: `invoice-processed.html`
- **Channel**: Email
- **Variables**: invoiceId, invoiceNumber, totalAmount, currency, processedAt

### PDF Generated
- **Trigger**: PDF generation completed
- **Template**: `pdf-generated.html`
- **Channel**: Email
- **Variables**: invoiceId, invoiceNumber, documentUrl, fileSize, generatedAt

## REST API

### Send Notification
```bash
POST /api/v1/notifications
Content-Type: application/json

{
  "type": "INVOICE_RECEIVED",
  "channel": "EMAIL",
  "recipient": "user@example.com",
  "subject": "Invoice Received",
  "templateName": "invoice-received",
  "templateVariables": {
    "invoiceNumber": "INV-2025-001",
    "invoiceId": "uuid",
    "receivedAt": "2025-12-03 10:30:00"
  },
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "correlationId": "trace-id"
}

Response: 200 OK
{
  "notificationId": "uuid",
  "status": "SENT",
  "createdAt": "2025-12-03T10:30:00",
  "sentAt": "2025-12-03T10:30:05"
}
```

### Get Notification by ID
```bash
GET /api/v1/notifications/{id}

Response: 200 OK
{
  "id": "uuid",
  "type": "INVOICE_RECEIVED",
  "channel": "EMAIL",
  "status": "SENT",
  "recipient": "user@example.com",
  "subject": "Invoice Received",
  "createdAt": "2025-12-03T10:30:00",
  "sentAt": "2025-12-03T10:30:05"
}
```

### Get Notifications by Invoice
```bash
GET /api/v1/notifications/invoice/{invoiceId}

Response: 200 OK
[
  {
    "id": "uuid1",
    "type": "INVOICE_RECEIVED",
    "status": "SENT",
    "sentAt": "2025-12-03T10:30:05"
  },
  {
    "id": "uuid2",
    "type": "INVOICE_PROCESSED",
    "status": "SENT",
    "sentAt": "2025-12-03T10:35:10"
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

### Consumed Topics

| Topic | Event | Handler |
|-------|-------|---------|
| `invoice.received` | InvoiceReceivedEvent | InvoiceEventListener |
| `invoice.processed` | InvoiceProcessedEvent | InvoiceEventListener |
| `pdf.generated` | PdfGeneratedEvent | InvoiceEventListener |

### Event Schemas

**InvoiceReceivedEvent**
```json
{
  "eventId": "uuid",
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "source": "REST",
  "receivedAt": "2025-12-03T10:30:00",
  "correlationId": "trace-id"
}
```

**InvoiceProcessedEvent**
```json
{
  "eventId": "uuid",
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "totalAmount": 1500.00,
  "currency": "THB",
  "processedAt": "2025-12-03T10:35:00",
  "correlationId": "trace-id"
}
```

**PdfGeneratedEvent**
```json
{
  "eventId": "uuid",
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "documentId": "doc-uuid",
  "documentUrl": "http://storage:8084/api/v1/documents/doc-uuid",
  "fileSize": 125000,
  "xmlEmbedded": true,
  "digitallySigned": false,
  "generatedAt": "2025-12-03T10:40:00",
  "correlationId": "trace-id"
}
```

## Email Templates

Templates are located in `src/main/resources/templates/` and use Thymeleaf syntax.

### Template Variables

**invoice-received.html**
- `invoiceNumber` - Invoice number
- `invoiceId` - Invoice UUID
- `receivedAt` - Timestamp of receipt
- `source` - Source channel (REST, Kafka)

**invoice-processed.html**
- `invoiceNumber` - Invoice number
- `invoiceId` - Invoice UUID
- `totalAmount` - Formatted total (e.g., "1,500.00")
- `currency` - Currency code (e.g., "THB")
- `processedAt` - Processing timestamp

**pdf-generated.html**
- `invoiceNumber` - Invoice number
- `documentId` - Document UUID
- `documentUrl` - Download URL
- `fileSize` - Human-readable size (e.g., "125 KB")
- `generatedAt` - Generation timestamp

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
| metadata | JSONB | Additional metadata |
| template_name | VARCHAR(100) | Template file name |
| template_variables | JSONB | Template variable values |
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
1. PostgreSQL 16+ running
2. Kafka broker running
3. SMTP server credentials (Gmail, SendGrid, AWS SES, etc.)

### Build
```bash
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
    "invoiceNumber": "INV-2025-001",
    "totalAmount": "1,500.00"
  }
}
```

### Webhook Payload
```json
{
  "notificationId": "uuid",
  "type": "INVOICE_PROCESSED",
  "subject": "Invoice Processed: INV-2025-001",
  "body": null,
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "correlationId": "trace-id",
  "timestamp": "2025-12-03T10:35:00",
  "metadata": {},
  "data": {
    "invoiceNumber": "INV-2025-001",
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
```bash
mvn verify
```

### Manual Testing - Send Test Email
```bash
curl -X POST http://localhost:8085/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "type": "INVOICE_RECEIVED",
    "channel": "EMAIL",
    "recipient": "test@example.com",
    "subject": "Test Notification",
    "templateName": "invoice-received",
    "templateVariables": {
      "invoiceNumber": "TEST-001",
      "invoiceId": "test-uuid",
      "receivedAt": "2025-12-03 10:30:00"
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
grep "EnableScheduling" src/main/java/com/invoice/notification/NotificationServiceApplication.java

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
grep "invoice.received" src/main/resources/application.yml
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
src/main/java/com/invoice/notification/
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
    ├── messaging/          # Kafka listeners, event DTOs
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
