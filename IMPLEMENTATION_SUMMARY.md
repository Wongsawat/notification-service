# Notification Service - Implementation Summary

## Overview

This document provides a comprehensive summary of the Notification Service implementation, including architecture decisions, implementation details, and integration points.

## Implementation Status

✅ **COMPLETED** - The service is fully implemented and ready for deployment

### Completed Components

1. ✅ Domain model with DDD patterns and state machine
2. ✅ PostgreSQL persistence layer with JPA
3. ✅ Multi-channel notification support (Email, Webhook)
4. ✅ Thymeleaf template engine for HTML emails
5. ✅ **Apache Camel 4.14.4** Kafka event-driven integration (16 routes)
6. ✅ **Saga orchestrator pattern** integration with outbox pattern
7. ✅ **Debezium CDC** connector for reliable event publishing
8. ✅ REST API for manual notification triggering
9. ✅ Automatic retry mechanism with scheduled tasks
10. ✅ Docker containerization
11. ✅ Service discovery integration
12. ✅ Actuator monitoring endpoints

## Architecture

### Clean Architecture Layers

```
┌─────────────────────────────────────────────┐
│        Presentation Layer                   │
│  (NotificationController - REST API)        │
├─────────────────────────────────────────────┤
│        Application Layer                    │
│  (NotificationService - Orchestration)      │
├─────────────────────────────────────────────┤
│          Domain Layer                       │
│  (Notification, NotificationSender)         │
├─────────────────────────────────────────────┤
│      Infrastructure Layer                   │
│  (PostgreSQL, Kafka, Email, Webhooks)       │
└─────────────────────────────────────────────┘
```

### Domain-Driven Design (DDD)

**Aggregate Root:**
- `Notification` - Encapsulates notification lifecycle and business rules

**Value Objects:**
- `NotificationType` enum - INVOICE_RECEIVED, INVOICE_PROCESSED, PDF_GENERATED, etc.
- `NotificationChannel` enum - EMAIL, SMS, WEBHOOK, IN_APP
- `NotificationStatus` enum - PENDING, SENDING, SENT, FAILED, RETRYING

**Domain Services:**
- `NotificationSender` - Abstract notification delivery interface
- `EmailNotificationSender` - Email implementation using Spring Mail
- `WebhookNotificationSender` - HTTP webhook implementation using WebClient

**Repository:**
- `NotificationRepository` - Domain repository interface
- `NotificationRepositoryImpl` - JPA implementation with entity mapping

## Key Design Decisions

### 1. Multi-Channel Architecture

**Decision:** Implement strategy pattern for pluggable notification channels

**Rationale:**
- **Extensibility**: Easy to add new channels (SMS, push notifications)
- **Testability**: Each sender can be tested independently
- **Single Responsibility**: Each sender handles one channel type

**Implementation:**
- `NotificationSender` interface defines contract
- `EmailNotificationSender` uses Spring Mail with SMTP
- `WebhookNotificationSender` uses WebClient for HTTP POST
- Service automatically selects sender based on `NotificationChannel`

**Trade-offs:**
- More classes but better separation of concerns
- Slightly more complexity but much better maintainability

### 2. Template-Based Emails

**Decision:** Use Thymeleaf for HTML email templates instead of hardcoded strings

**Rationale:**
- **Maintainability**: Non-developers can edit HTML templates
- **Rich Formatting**: Professional HTML emails with CSS styling
- **Reusability**: Same template for different invoices
- **Localization**: Easy to add multi-language support

**Implementation:**
- Templates in `src/main/resources/templates/`
- Thymeleaf syntax: `th:text="${variableName}"`
- TemplateEngine component renders templates with variables
- Fallback to plain text if template not found

### 3. Event-Driven Integration

**Decision:** Use Kafka for event consumption instead of REST polling

**Rationale:**
- **Decoupling**: Notification service doesn't need to know about other services
- **Reliability**: Kafka ensures at-least-once delivery
- **Scalability**: Multiple consumers can process events in parallel
- **Audit Trail**: Kafka log provides event history

**Implementation:**
- `InvoiceEventListener` consumes three topics:
  - `invoice.received` → Send "Invoice Received" email
  - `invoice.processed` → Send "Invoice Processed" email
  - `pdf.generated` → Send "PDF Ready" email with download link
- Separate `KafkaListenerContainerFactory` for each event type
- Manual acknowledgment for reliability

### 4. State Machine for Notification Lifecycle

**Decision:** Implement state machine pattern in Notification aggregate

**Rationale:**
- **Correctness**: Ensures valid state transitions
- **Clarity**: Clear lifecycle from PENDING → SENDING → SENT/FAILED
- **Retry Support**: FAILED → RETRYING → SENDING (with retry limits)

**State Transitions:**
```
PENDING ──────────────────> SENDING ──────────> SENT
   ↑                            │
   │                            ↓
   │                         FAILED
   │                            │
   └────────── RETRYING <───────┘
```

**Business Rules:**
- Can only start sending from PENDING or RETRYING
- Can only mark sent from SENDING
- Can only mark failed from SENDING
- Can only retry from FAILED (with retry count < maxRetries)

### 5. Scheduled Retry Mechanism

**Decision:** Use Spring @Scheduled for automatic retry of failed notifications

**Rationale:**
- **Reliability**: Transient failures (network issues, SMTP outages) are retried
- **No Manual Intervention**: System self-heals
- **Configurable**: Retry interval and max attempts are configurable

**Implementation:**
- `retryFailedNotifications()` runs every 5 minutes
- Queries notifications with status=FAILED and retryCount < maxRetries
- Prepares each for retry and sends asynchronously
- Exponential backoff implicit via retry interval

### 6. PostgreSQL with TEXT + JPA AttributeConverter

**Decision:** Use TEXT columns with JPA `AttributeConverter` instead of JSONB for JSON storage

**Rationale:**
- **Database Portability**: Works across PostgreSQL, MySQL, H2 (testing)
- **Simplicity**: No native JSONB operators needed for this use case
- **Consistency**: Aligns with sibling services in the microservices ecosystem

**Implementation:**
- TEXT columns: `metadata`, `template_variables`
- `JsonMapConverter` (JPA `AttributeConverter`) handles JSON serialization via Jackson
- Transparent conversion: Domain model works with `Map<String, Object>`

**Trade-off**: Cannot use PostgreSQL JSONB operators (`->`, `->>>`), but not required for current queries.

### 7. Apache Camel for Kafka Integration

**Decision:** Use Apache Camel 4.14.4 RouteBuilder instead of Spring Kafka `@KafkaListener`

**Rationale:**
- **Unified Integration**: Camel provides consistent patterns for all message handling
- **Enterprise Integration Patterns**: Built-in support for error handling, retries, dead letter channels
- **Route Visibility**: Declarative routes easier to monitor and understand
- **Flexibility**: Easy to add transformations, content-based routing, aggregation

**Implementation:**
- 16 Camel routes defined in `NotificationEventRoutes.java`
- Dead Letter Channel with exponential backoff (3 retries, max 30s)
- Manual offset control (`autoCommitEnable=false`)
- Route-specific error handling
- All event DTOs extend `IntegrationEvent` from saga-commons for standardization

**Trade-off**: Additional dependency (camel-kafka-starter), but provides better enterprise integration capabilities.

### 8. Saga Orchestrator Pattern Integration

**Decision:** Integrate with saga orchestrator as an **observer** using transactional outbox pattern

**Rationale:**
- **Loose Coupling**: Service observes saga lifecycle without participating in execution
- **Reliable Events**: Outbox pattern + Debezium CDC guarantees at-least-once delivery
- **Monitoring**: Saga completed/failed notifications provide visibility into orchestration
- **No Compensation**: As an observer, no compensation logic needed

**Implementation:**
- 4 saga lifecycle event DTOs (`SagaStartedEvent`, `SagaStepCompletedEvent`, `SagaCompletedEvent`, `SagaFailedEvent`)
- 4 Camel routes consuming `saga.lifecycle.*` topics
- 2 email templates (saga-completed.html with green theme, saga-failed.html with red alert theme)
- `outbox_events` table for future event publishing (currently acts as consumer only)
- Debezium CDC connector monitoring outbox table

**Integration Points:**
- **Consumes**: `saga.lifecycle.completed`, `saga.lifecycle.failed` → Creates email notifications
- **Logs**: `saga.lifecycle.started`, `saga.lifecycle.step-completed` → Audit trail only
- **Future**: Can publish `NotificationSentEvent` via outbox pattern

**Dependencies:**
- `saga-commons` (1.0.0-SNAPSHOT) - Outbox infrastructure + IntegrationEvent base class
- `jackson-datatype-jsr310` - Java 8 Date/Time API support for `Instant` fields

### 9. Event Standardization via IntegrationEvent

**Decision:** All event DTOs extend `IntegrationEvent` from saga-commons

**Rationale:**
- **Consistency**: Standard event structure across all microservices
- **Type Safety**: eventId as UUID (not String), timestamps as Instant (not String/LocalDateTime)
- **Metadata**: Built-in eventId, occurredAt, eventType, version fields
- **Serialization**: Jackson-friendly with @JsonCreator for deserialization
- **Immutability**: All fields are final with @Getter (no @Data/@Builder)

**Implementation:**
```java
@Getter
public class InvoiceProcessedEvent extends IntegrationEvent {
    private final String invoiceId;
    private final String invoiceNumber;
    private final BigDecimal totalAmount;
    private final String currency;
    private final String correlationId;

    // Constructor 1: For creating new events (auto-generates metadata)
    public InvoiceProcessedEvent(String invoiceId, String invoiceNumber,
                                  BigDecimal totalAmount, String currency,
                                  String correlationId) {
        super();
        this.invoiceId = invoiceId;
        // ...
    }

    // Constructor 2: @JsonCreator for deserialization
    @JsonCreator
    public InvoiceProcessedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("invoiceId") String invoiceId,
        // ... other fields
    ) {
        super(eventId, occurredAt, eventType, version);
        this.invoiceId = invoiceId;
        // ...
    }
}
```

**Refactored Events (11 total):**
- Processing Events (7): InvoiceProcessedEvent, TaxInvoiceProcessedEvent, PdfGeneratedEvent, PdfSignedEvent, EbmsSentEvent, DocumentReceivedEvent, DocumentReceivedCountingEvent
- Saga Events (4): SagaStartedEvent, SagaStepCompletedEvent, SagaCompletedEvent, SagaFailedEvent

**Benefits:**
- Consistent event metadata across microservices
- Immutable event objects
- Type-safe UUID eventId
- Instant timestamps (ISO-8601 compatible)
- Schema versioning support (version field)
- Jackson serialization/deserialization handled correctly

## Implementation Details

### Domain Model

#### Notification Aggregate

```java
public class Notification {
    private UUID id;
    private NotificationType type;
    private NotificationChannel channel;
    private NotificationStatus status;
    private String recipient;
    private String subject;
    private String body;
    private Map<String, Object> metadata;
    private String templateName;
    private Map<String, Object> templateVariables;
    private String invoiceId;
    private String invoiceNumber;
    private String correlationId;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private LocalDateTime failedAt;
    private int retryCount;
    private String errorMessage;

    // Factory methods
    public static Notification create(...);
    public static Notification createFromTemplate(...);

    // State machine methods
    public void markSending();
    public void markSent();
    public void markFailed(String errorMessage);
    public void prepareRetry();
    public boolean canRetry(int maxRetries);

    // Helper methods
    public void addMetadata(String key, Object value);
    public void addTemplateVariable(String key, Object value);
    public boolean usesTemplate();
}
```

**Factory Methods:**
- `create()` - Plain text notification
- `createFromTemplate()` - Template-based notification

**State Transitions:**
- Guards ensure valid state changes
- `IllegalStateException` thrown for invalid transitions

### Application Service

#### NotificationService

```java
@Service
public class NotificationService {
    // Send notification synchronously
    public Notification sendNotification(Notification notification);

    // Send notification asynchronously
    @Async
    public void sendNotificationAsync(Notification notification);

    // Create and send from template
    public Notification createAndSend(...);

    // Scheduled retry task (every 5 minutes)
    @Scheduled(fixedDelayString = "${app.notification.retry-interval:300000}")
    public void retryFailedNotifications();

    // Scheduled pending processor (every 1 minute)
    @Scheduled(fixedDelayString = "${app.notification.processing-interval:60000}")
    public void processPendingNotifications();

    // Get statistics
    public Map<String, Long> getStatistics();
}
```

**Orchestration Flow:**
1. Save notification as PENDING
2. Mark as SENDING
3. Find appropriate sender (Email, Webhook)
4. Delegate to sender
5. Mark as SENT or FAILED
6. If failed, scheduled task will retry

### Notification Channels

#### EmailNotificationSender

```java
@Component
public class EmailNotificationSender implements NotificationSender {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public void send(Notification notification) throws NotificationException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(notification.getRecipient());
        helper.setSubject(notification.getSubject());

        String body = notification.usesTemplate()
            ? templateEngine.render(notification.getTemplateName(), notification.getTemplateVariables())
            : notification.getBody();

        helper.setText(body, true); // HTML

        mailSender.send(message);
    }

    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.EMAIL;
    }
}
```

**Features:**
- STARTTLS encryption
- HTML email support
- Template rendering
- Metadata as email headers

#### WebhookNotificationSender

```java
@Component
public class WebhookNotificationSender implements NotificationSender {
    private final WebClient webClient;

    public void send(Notification notification) throws NotificationException {
        Map<String, Object> payload = buildPayload(notification);

        webClient.post()
            .uri(notification.getRecipient())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(30))
            .block();
    }

    private Map<String, Object> buildPayload(Notification notification) {
        // Build JSON payload with notification data
    }
}
```

**Features:**
- 30-second timeout
- JSON payload
- Error status handling
- Reactive HTTP client

#### TemplateEngine

```java
@Component
public class TemplateEngine {
    private final SpringTemplateEngine thymeleafEngine;

    public String render(String templateName, Map<String, Object> variables)
        throws TemplateException {
        Context context = new Context();
        variables.forEach(context::setVariable);
        return thymeleafEngine.process(templateName, context);
    }
}
```

### Kafka Integration

#### Event Listeners

```java
@Component
public class InvoiceEventListener {
    @KafkaListener(topics = "${kafka.topics.invoice-received}")
    public void handleInvoiceReceived(InvoiceReceivedEvent event) {
        Map<String, Object> templateVariables = Map.of(
            "invoiceId", event.getInvoiceId(),
            "invoiceNumber", event.getInvoiceNumber(),
            "receivedAt", event.getReceivedAt().format(DATE_FORMATTER)
        );

        Notification notification = Notification.createFromTemplate(
            NotificationType.INVOICE_RECEIVED,
            NotificationChannel.EMAIL,
            defaultRecipient,
            "invoice-received",
            templateVariables
        );

        notificationService.sendNotificationAsync(notification);
    }

    @KafkaListener(topics = "${kafka.topics.invoice-processed}")
    public void handleInvoiceProcessed(InvoiceProcessedEvent event) { /* ... */ }

    @KafkaListener(topics = "${kafka.topics.pdf-generated}")
    public void handlePdfGenerated(PdfGeneratedEvent event) { /* ... */ }
}
```

**Consumer Configuration:**
- Group ID: `notification-service`
- Concurrency: 2 consumer threads per topic
- Manual acknowledgment
- Trusted packages: `com.invoice.*`

### REST API

#### NotificationController

```java
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    @PostMapping
    public ResponseEntity<Map<String, Object>> sendNotification(@RequestBody NotificationRequest request);

    @GetMapping("/{id}")
    public ResponseEntity<Notification> getNotification(@PathVariable UUID id);

    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<List<Notification>> getNotificationsByInvoice(@PathVariable String invoiceId);

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Notification>> getNotificationsByStatus(@PathVariable NotificationStatus status);

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Long>> getStatistics();

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, String>> retryNotification(@PathVariable UUID id);
}
```

### Database Schema

**Table: notifications**
- Primary key: UUID `id`
- Indexes on: status, invoice_id, recipient, created_at, type
- Partial index on: (status, retry_count) WHERE status = 'FAILED'
- JSONB columns: metadata, template_variables

**Migration Strategy:**
- Flyway for versioned migrations
- V1__create_notifications_table.sql
- Baseline on migrate for existing databases

## Dependencies

### Core Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.2.5 | Framework |
| Spring Data JPA | 3.2.5 | Database ORM |
| Spring Mail | 3.2.5 | Email sending |
| Spring Kafka | 3.1.2 | Kafka integration |
| Spring Cloud Netflix Eureka | 4.1.0 | Service discovery |
| Thymeleaf | 3.1.2 | Template engine |
| WebFlux | 3.2.5 | Reactive HTTP client |
| PostgreSQL Driver | Latest | Database driver |
| Flyway | 10.x | Database migrations |
| Lombok | 1.18.30 | Boilerplate reduction |

## Email Templates

### invoice-received.html

Professional HTML email with:
- Header with green background
- Invoice details in bordered box
- Footer with copyright notice
- Responsive design (max-width: 600px)

**Variables:**
- `invoiceNumber`
- `invoiceId`
- `receivedAt`
- `source`

### invoice-processed.html

Success notification with:
- Blue header with checkmark
- Invoice details including total amount
- Processing completion message

**Variables:**
- `invoiceNumber`
- `invoiceId`
- `totalAmount`
- `currency`
- `processedAt`

### pdf-generated.html

Document ready notification with:
- Orange header
- Document details and file size
- Download button (CTA)
- Compliance note about embedded XML

**Variables:**
- `invoiceNumber`
- `documentId`
- `documentUrl`
- `fileSize`
- `generatedAt`

## Configuration

### Key Configuration Properties

```yaml
app:
  notification:
    enabled: true                    # Enable/disable notifications
    default-recipient: admin@example.com
    max-retries: 3                   # Maximum retry attempts
    retry-interval: 300000           # 5 minutes in milliseconds
    processing-interval: 60000       # 1 minute

spring:
  mail:
    host: smtp.gmail.com
    port: 587
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

  task:
    scheduling.pool.size: 5          # Scheduled task threads
    execution.pool.core-size: 5      # Async execution threads
```

## Error Handling

### Email Sending Errors

**Strategy:** Catch and mark as failed, schedule retry

**Handled Exceptions:**
- `MessagingException` - SMTP errors, connection issues
- `TemplateException` - Template rendering errors
- `MailException` - General mail sending errors

**Error Storage:**
- Exception message stored in `error_message` column
- Failed timestamp in `failed_at`
- Increment `retry_count`

### Webhook Errors

**Strategy:** Catch HTTP errors, mark as failed

**Handled Exceptions:**
- `WebClientResponseException` - HTTP 4xx, 5xx errors
- `TimeoutException` - 30-second timeout exceeded
- `WebClientRequestException` - Connection errors

### Kafka Consumer Errors

**Strategy:** Log and continue (don't block event processing)

**Behavior:**
- Exceptions caught in listener methods
- Error logged with event details
- Notification marked as FAILED
- Scheduled retry will attempt redelivery

## Performance Considerations

### Throughput

**Expected Load:**
- 1,000 notifications/hour (low volume)
- 10,000 notifications/hour (medium volume)
- 100,000 notifications/hour (high volume)

**Scalability:**
- Stateless service → Horizontal scaling
- Async processing → Non-blocking I/O
- Connection pooling → Efficient database usage
- Kafka consumer groups → Parallel event processing

### Optimization Opportunities

1. **Email Batching**: Send multiple emails in single SMTP connection
2. **Template Caching**: Cache rendered templates (Thymeleaf default)
3. **Database Indexing**: Composite indexes for complex queries
4. **Connection Pooling**: HikariCP for database, reuse WebClient
5. **Async Everything**: Make all notification sending async

## Monitoring and Observability

### Actuator Endpoints

- `/actuator/health` - PostgreSQL + Kafka health
- `/actuator/metrics` - Notification metrics
- `/actuator/prometheus` - Prometheus format metrics

### Key Metrics to Monitor

1. **Send Rate**: Notifications sent per minute
2. **Failure Rate**: Failed notifications per minute
3. **Retry Rate**: Retry attempts per minute
4. **Processing Time**: P50, P95, P99 latencies
5. **Kafka Lag**: Consumer lag on each topic
6. **Database Connections**: Active connections
7. **Email Queue**: Pending notifications count

### Logging

**Log Levels:**
- INFO: Notification sent, event received
- WARN: Retry attempts, template not found
- ERROR: Send failures, Kafka errors

**Structured Logging (Future):**
- Correlation ID propagation
- Request tracing with Sleuth
- Centralized log aggregation (ELK)

## Integration with Other Services

### Invoice Intake Service

**Integration:** Kafka event subscription

**Flow:**
1. Invoice Intake validates and stores invoice
2. Publishes `InvoiceReceivedEvent`
3. Notification Service sends "Invoice Received" email

### Invoice Processing Service

**Integration:** Kafka event subscription

**Flow:**
1. Invoice Processing enriches and calculates totals
2. Publishes `InvoiceProcessedEvent`
3. Notification Service sends "Invoice Processed" email

### PDF Generation Service

**Integration:** Kafka event subscription

**Flow:**
1. PDF Generation creates PDF/A-3 document
2. Publishes `PdfGeneratedEvent` with download URL
3. Notification Service sends "PDF Ready" email with link

## Deployment

### Local Development

```bash
# Start dependencies
docker-compose up -d postgres kafka

# Run service
mvn spring-boot:run
```

### Docker Compose

```yaml
notification-service:
  image: notification-service:latest
  ports:
    - "8085:8085"
  environment:
    DB_HOST: postgres
    KAFKA_BROKERS: kafka:29092
    MAIL_HOST: smtp.gmail.com
    MAIL_USERNAME: ${MAIL_USERNAME}
    MAIL_PASSWORD: ${MAIL_PASSWORD}
  depends_on:
    - postgres
    - kafka
```

### Kubernetes (Future)

**Deployment Strategy:**
- Deployment with 3 replicas
- HorizontalPodAutoscaler (CPU + custom metrics)
- ConfigMap for application.yml
- Secret for SMTP credentials
- Service for internal communication
- Ingress for external API access (if needed)

## Testing Infrastructure

### Unit Tests (147 tests)

Comprehensive test coverage across all layers:
- **Domain model tests (4):** Notification, NotificationStatus, NotificationChannel, NotificationType
- **Application layer tests (2):** NotificationService, NotificationController
- **Infrastructure tests (5):** NotificationRepositoryImpl, JsonMapConverter, EmailNotificationSender, WebhookNotificationSender, TemplateEngine
- **Saga event tests (4):** SagaStartedEvent, SagaStepCompletedEvent, SagaCompletedEvent, SagaFailedEvent
- **Application test (1):** NotificationServiceApplicationTest

**Run unit tests:**
```bash
mvn test
```

### Integration Tests (2 tests)

Integration tests verify end-to-end Kafka event consumption using Apache Camel routes with Testcontainers.

**Test Infrastructure:**
- **AbstractKafkaConsumerTest** - Base class with shared infrastructure:
  - TestContainers configuration (PostgreSQL port 5433, Kafka port 9093)
  - Mock EmailNotificationSender and WebhookNotificationSender
  - Helper methods for awaiting notification status changes
  - Database cleanup between tests
  - ObjectMapper configured for JavaTimeModule (Instant support)
- **Test Configuration:** `consumer-test` profile, `TestKafkaProducerConfig`, `ConsumerTestConfiguration`

**Integration Test Coverage:**
1. **shouldConsumeInvoiceProcessedEvent()** - Tests `invoice.processed` topic consumption
2. **shouldConsumeTaxInvoiceProcessedEvent()** - Tests `taxinvoice.processed` topic consumption

**Run integration tests:**
```bash
# Start test containers first
cd ../../../invoice-microservices
./scripts/test-containers-start.sh

# Run all integration tests
cd services/notification-service
mvn test -Pintegration -Dtest=KafkaConsumerIntegrationTest

# Run specific test
mvn test -Pintegration -Dtest=KafkaConsumerIntegrationTest#shouldConsumeTaxInvoiceProcessedEvent
```

**Test Validation:**
- Event unmarshalling from Kafka JSON (Camel + Jackson)
- Notification aggregate creation with correct type and template
- Template variables populated correctly
- Async processing completion (status reaches SENT)
- Database persistence of all notification fields
- Correct subject, recipient, and correlation ID

### JaCoCo Coverage

90% line coverage requirement per package (enforced via `mvn verify`)

## Future Enhancements

### Short-Term (Next Sprint)

1. ~~**Unit Tests**: Comprehensive test coverage~~ **COMPLETED** (147 tests)
2. ~~**Integration Tests**: Testcontainers-based tests~~ **COMPLETED** (2 tests)
3. **Additional Integration Tests**: Add tests for remaining event types (PdfGeneratedEvent, PdfSignedEvent, SagaCompletedEvent, SagaFailedEvent)
4. **Camel Route Unit Tests**: Add unit tests for individual Camel routes
5. **SMS Support**: Twilio or AWS SNS integration
6. **Webhook Signatures**: HMAC-SHA256 for security
7. **Delivery Reports**: Track email opens and clicks

### Medium-Term (Next Quarter)

1. **Push Notifications**: Firebase Cloud Messaging
2. **Template Editor**: Web UI for template management
3. **Unsubscribe Management**: Respect user preferences
4. **Multi-language**: Localized templates
5. **Rich Media**: Attach PDFs to emails

### Long-Term (Future)

1. **A/B Testing**: Test different email templates
2. **Personalization**: Dynamic content based on user preferences
3. **Event Sourcing**: Full audit trail
4. **GraphQL API**: Alternative to REST
5. **Real-time WebSocket**: Live notification status
6. **Machine Learning**: Optimal send time prediction
7. **Advanced Analytics**: Engagement metrics dashboard

## Known Limitations

1. **No Email Verification**: No bounce handling or invalid email detection
2. **No Rate Limiting**: Potential for email flooding
3. **No Signature Verification**: Webhooks not cryptographically signed
4. **Synchronous SMTP**: Blocks thread during email send
5. **No Dead Letter Queue**: Failed events lost after max retries exceeded
6. **No Batching**: Each notification sent individually
7. **No Unsubscribe**: No mechanism to opt-out of notifications

## Troubleshooting

### Common Issues

**1. Emails not sending**
- Check SMTP credentials in logs
- Test SMTP connection: `telnet smtp.gmail.com 587`
- Verify firewall allows outbound port 587
- Check Gmail "Less secure apps" or App Password

**2. Template rendering fails**
- Check template exists: `ls src/main/resources/templates/`
- Verify Thymeleaf syntax: `th:text="${var}"`
- Check logs for `TemplateException`

**3. Kafka events not consumed**
- Verify topic exists: `kafka-topics.sh --list`
- Check consumer group: `kafka-consumer-groups.sh --describe`
- Review Kafka connectivity in logs

**4. Notifications stuck in PENDING**
- Check scheduled tasks are enabled: `@EnableScheduling`
- Verify processing interval: `app.notification.processing-interval`
- Manually trigger: POST `/api/v1/notifications/{id}/retry`

**5. Database connection failures**
- Verify PostgreSQL running: `docker ps | grep postgres`
- Test connection: `psql -h localhost -U postgres -d notification_db`
- Check connection pool: `/actuator/metrics/hikaricp.connections.active`

## Conclusion

The Notification Service is a production-ready microservice that provides reliable multi-channel notifications with:

- ✅ Clean architecture and DDD patterns
- ✅ Event-driven integration via Kafka
- ✅ Professional HTML email templates
- ✅ Webhook support for external integrations
- ✅ Automatic retry with state machine
- ✅ RESTful API for manual triggering
- ✅ Comprehensive monitoring and logging
- ✅ Docker containerization

The service is ready for deployment and can scale horizontally to handle increased notification volume. Future enhancements will add SMS support, advanced analytics, and personalization features.

## References

- [Design Document](../../../teda/docs/design/invoice-microservices-design.md)
- [Spring Mail Documentation](https://docs.spring.io/spring-framework/reference/integration/email.html)
- [Thymeleaf Documentation](https://www.thymeleaf.org/documentation.html)
- [Spring Kafka Documentation](https://docs.spring.io/spring-kafka/reference/)
- [WebClient Documentation](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)

---

**Author:** wpanther
**Date:** 2025-12-04
**Version:** 1.0.0
