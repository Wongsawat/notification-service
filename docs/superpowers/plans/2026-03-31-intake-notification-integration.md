# Intake → Notification Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `notification-service` to consume `trace.document.received` events from `document-intake-service` and persist per-status intake statistics to a dedicated table, exposed via a new REST endpoint.

**Architecture:** A new Camel Route 19 deserializes `DocumentReceivedTraceEvent` from Kafka and delegates to `DocumentIntakeStatUseCase`, which persists a row per event to `document_intake_stats`. Dead Route 7 (`document.received` / `DocumentReceivedCountingEvent`) is removed. A new `GET /api/v1/notifications/statistics/intake` endpoint returns aggregated counts.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel, Spring Data JPA, H2 (tests), PostgreSQL (prod), Flyway, JUnit 5, Mockito, AssertJ, MockMvc.

**Worktree:** All work happens in the isolated worktree at `/home/wpanther/projects/etax/invoice-microservices/services/notification-service-intake-stats` (branch `feat/intake-notification-stats`). All `cd` and `git` commands in this plan use that path.

---

## File Map

**Create:**
- `src/main/resources/db/migration/V5__create_document_intake_stats_table.sql`
- `src/main/java/com/wpanther/notification/domain/model/DocumentIntakeStat.java`
- `src/main/java/com/wpanther/notification/domain/repository/DocumentIntakeStatRepository.java`
- `src/main/java/com/wpanther/notification/application/port/in/event/DocumentReceivedTraceEvent.java`
- `src/main/java/com/wpanther/notification/application/usecase/DocumentIntakeStatUseCase.java`
- `src/main/java/com/wpanther/notification/application/dto/DocumentIntakeStatsResponse.java`
- `src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/DocumentIntakeStatEntity.java`
- `src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/SpringDataDocumentIntakeStatRepository.java`
- `src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/JpaDocumentIntakeStatRepository.java`
- `src/test/java/com/wpanther/notification/domain/model/DocumentIntakeStatTest.java`
- `src/test/java/com/wpanther/notification/application/port/in/event/DocumentReceivedTraceEventTest.java`
- `src/test/java/com/wpanther/notification/infrastructure/adapter/out/persistence/JpaDocumentIntakeStatRepositoryTest.java`

**Modify:**
- `src/main/resources/application.yml` — add `trace-document-received`, remove `document-received`
- `src/test/resources/application-test.yml` — add `trace-document-received` and missing topics
- `src/main/java/com/wpanther/notification/infrastructure/config/KafkaTopicsConfig.java` — add `traceDocumentReceived`, remove `documentReceived`
- `src/main/java/com/wpanther/notification/application/usecase/DocumentReceivedEventUseCase.java` — remove `handleDocumentCounting`
- `src/main/java/com/wpanther/notification/application/service/NotificationService.java` — add `DocumentIntakeStatUseCase` impl, remove `handleDocumentCounting`
- `src/main/java/com/wpanther/notification/infrastructure/adapter/in/kafka/NotificationEventRoutes.java` — add Route 19, remove Route 7
- `src/main/java/com/wpanther/notification/infrastructure/adapter/in/rest/NotificationController.java` — add stats/intake endpoint
- `src/test/java/com/wpanther/notification/application/service/NotificationServiceTest.java` — add `handleIntakeStat` test
- `src/test/java/com/wpanther/notification/infrastructure/adapter/in/rest/NotificationControllerTest.java` — add intake stats endpoint tests

**Delete:**
- `src/main/java/com/wpanther/notification/application/port/in/event/DocumentReceivedCountingEvent.java`

---

## Task 1: Flyway migration

**Files:**
- Create: `src/main/resources/db/migration/V5__create_document_intake_stats_table.sql`

- [ ] **Step 1: Create migration script**

```sql
CREATE TABLE IF NOT EXISTS document_intake_stats (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     VARCHAR(255) NOT NULL,
    document_type   VARCHAR(100),
    document_number VARCHAR(255),
    status          VARCHAR(50)  NOT NULL,
    source          VARCHAR(100),
    correlation_id  VARCHAR(255),
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_intake_stats_document_id    ON document_intake_stats (document_id);
CREATE INDEX IF NOT EXISTS idx_intake_stats_status         ON document_intake_stats (status);
CREATE INDEX IF NOT EXISTS idx_intake_stats_document_type  ON document_intake_stats (document_type);
CREATE INDEX IF NOT EXISTS idx_intake_stats_occurred_at    ON document_intake_stats (occurred_at);
```

- [ ] **Step 2: Commit**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/notification-service-intake-stats
git add src/main/resources/db/migration/V5__create_document_intake_stats_table.sql
git commit -m "feat: add V5 migration for document_intake_stats table"
```

---

## Task 2: `DocumentIntakeStat` domain model + unit test

**Files:**
- Create: `src/main/java/com/wpanther/notification/domain/model/DocumentIntakeStat.java`
- Create: `src/test/java/com/wpanther/notification/domain/model/DocumentIntakeStatTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/wpanther/notification/domain/model/DocumentIntakeStatTest.java`:
```java
package com.wpanther.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentIntakeStat domain model tests")
class DocumentIntakeStatTest {

    @Test
    @DisplayName("Should build with all fields")
    void shouldBuildWithAllFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        DocumentIntakeStat stat = DocumentIntakeStat.builder()
            .id(id)
            .documentId("doc-001")
            .documentType("TAX_INVOICE")
            .documentNumber("TIV-2024-001")
            .status("RECEIVED")
            .source("API")
            .correlationId("corr-001")
            .occurredAt(now)
            .build();

        assertThat(stat.getId()).isEqualTo(id);
        assertThat(stat.getDocumentId()).isEqualTo("doc-001");
        assertThat(stat.getDocumentType()).isEqualTo("TAX_INVOICE");
        assertThat(stat.getDocumentNumber()).isEqualTo("TIV-2024-001");
        assertThat(stat.getStatus()).isEqualTo("RECEIVED");
        assertThat(stat.getSource()).isEqualTo("API");
        assertThat(stat.getCorrelationId()).isEqualTo("corr-001");
        assertThat(stat.getOccurredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should allow null optional fields")
    void shouldAllowNullOptionalFields() {
        DocumentIntakeStat stat = DocumentIntakeStat.builder()
            .id(UUID.randomUUID())
            .documentId("doc-002")
            .status("INVALID")
            .occurredAt(Instant.now())
            .build();

        assertThat(stat.getDocumentType()).isNull();
        assertThat(stat.getDocumentNumber()).isNull();
        assertThat(stat.getSource()).isNull();
        assertThat(stat.getCorrelationId()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/notification-service-intake-stats
mvn clean test -Dtest=DocumentIntakeStatTest -pl .
```
Expected: FAIL with `cannot find symbol: class DocumentIntakeStat`

- [ ] **Step 3: Implement `DocumentIntakeStat`**

`src/main/java/com/wpanther/notification/domain/model/DocumentIntakeStat.java`:
```java
package com.wpanther.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Value object representing a single intake status event for a document.
 * Persisted to document_intake_stats table for reporting.
 */
public class DocumentIntakeStat {

    private final UUID id;
    private final String documentId;
    private final String documentType;
    private final String documentNumber;
    private final String status;
    private final String source;
    private final String correlationId;
    private final Instant occurredAt;

    private DocumentIntakeStat(Builder builder) {
        this.id = builder.id;
        this.documentId = builder.documentId;
        this.documentType = builder.documentType;
        this.documentNumber = builder.documentNumber;
        this.status = builder.status;
        this.source = builder.source;
        this.correlationId = builder.correlationId;
        this.occurredAt = builder.occurredAt;
    }

    public UUID getId()             { return id; }
    public String getDocumentId()   { return documentId; }
    public String getDocumentType() { return documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public String getStatus()       { return status; }
    public String getSource()       { return source; }
    public String getCorrelationId() { return correlationId; }
    public Instant getOccurredAt()  { return occurredAt; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID id;
        private String documentId;
        private String documentType;
        private String documentNumber;
        private String status;
        private String source;
        private String correlationId;
        private Instant occurredAt;

        private Builder() {}

        public Builder id(UUID id)                     { this.id = id; return this; }
        public Builder documentId(String documentId)   { this.documentId = documentId; return this; }
        public Builder documentType(String documentType) { this.documentType = documentType; return this; }
        public Builder documentNumber(String documentNumber) { this.documentNumber = documentNumber; return this; }
        public Builder status(String status)           { this.status = status; return this; }
        public Builder source(String source)           { this.source = source; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder occurredAt(Instant occurredAt)  { this.occurredAt = occurredAt; return this; }

        public DocumentIntakeStat build() { return new DocumentIntakeStat(this); }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn clean test -Dtest=DocumentIntakeStatTest -pl .
```
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wpanther/notification/domain/model/DocumentIntakeStat.java \
        src/test/java/com/wpanther/notification/domain/model/DocumentIntakeStatTest.java
git commit -m "feat: add DocumentIntakeStat domain model"
```

---

## Task 3: `DocumentIntakeStatRepository` domain interface

**Files:**
- Create: `src/main/java/com/wpanther/notification/domain/repository/DocumentIntakeStatRepository.java`

- [ ] **Step 1: Create the interface**

```java
package com.wpanther.notification.domain.repository;

import com.wpanther.notification.domain.model.DocumentIntakeStat;

import java.util.List;
import java.util.Map;

public interface DocumentIntakeStatRepository {

    DocumentIntakeStat save(DocumentIntakeStat stat);

    /** Returns a map of status → count for all rows. */
    Map<String, Long> countByStatus();

    /** Returns a map of documentType → count for all rows. */
    Map<String, Long> countByDocumentType();

    /** Returns all stats for a document ordered by occurredAt ascending. */
    List<DocumentIntakeStat> findByDocumentId(String documentId);
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/wpanther/notification/domain/repository/DocumentIntakeStatRepository.java
git commit -m "feat: add DocumentIntakeStatRepository domain interface"
```

---

## Task 4: `DocumentReceivedTraceEvent` local DTO + JSON round-trip test

**Files:**
- Create: `src/main/java/com/wpanther/notification/application/port/in/event/DocumentReceivedTraceEvent.java`
- Create: `src/test/java/com/wpanther/notification/application/port/in/event/DocumentReceivedTraceEventTest.java`

The notification service must NOT import classes from `document-intake-service`. This local DTO mirrors the JSON shape published by intake.

> **Important:** The producer (document-intake-service) does NOT include `sagaId`, `traceType`, or `context` in its JSON. The `@JsonIgnoreProperties(ignoreUnknown = true)` annotation on TraceEvent ensures these are silently ignored during deserialization. The round-trip test below verifies this exact producer JSON shape.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/wpanther/notification/application/port/in/event/DocumentReceivedTraceEventTest.java`:
```java
package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentReceivedTraceEvent local DTO tests")
class DocumentReceivedTraceEventTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @Test
    @DisplayName("Should deserialize producer JSON with all fields")
    void shouldDeserializeProducerJson() throws Exception {
        // This is the exact JSON shape published by document-intake-service
        // Note: no sagaId, traceType, or context fields — producer never sets them
        String json = """
            {
              "eventId": "123e4567-e89b-12d3-a456-426614174000",
              "occurredAt": "2024-01-01T00:00:00Z",
              "eventType": "DocumentReceivedTraceEvent",
              "version": 1,
              "documentId": "doc-123",
              "documentType": "TAX_INVOICE",
              "documentNumber": "INV-001",
              "correlationId": "corr-123",
              "status": "RECEIVED",
              "source": "API"
            }
            """;

        DocumentReceivedTraceEvent event = MAPPER.readValue(json, DocumentReceivedTraceEvent.class);

        assertThat(event.getDocumentId()).isEqualTo("doc-123");
        assertThat(event.getDocumentType()).isEqualTo("TAX_INVOICE");
        assertThat(event.getDocumentNumber()).isEqualTo("INV-001");
        assertThat(event.getCorrelationId()).isEqualTo("corr-123");
        assertThat(event.getStatus()).isEqualTo("RECEIVED");
        assertThat(event.getSource()).isEqualTo("API");
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Should deserialize producer JSON with null correlationId")
    void shouldDeserializeProducerJsonWithNullCorrelationId() throws Exception {
        String json = """
            {
              "eventId": "123e4567-e89b-12d3-a456-426614174000",
              "occurredAt": "2024-01-01T00:00:00Z",
              "eventType": "DocumentReceivedTraceEvent",
              "version": 1,
              "documentId": "doc-456",
              "documentType": "INVOICE",
              "documentNumber": "INV-456",
              "correlationId": null,
              "status": "VALIDATED",
              "source": "KAFKA"
            }
            """;

        DocumentReceivedTraceEvent event = MAPPER.readValue(json, DocumentReceivedTraceEvent.class);

        assertThat(event.getDocumentId()).isEqualTo("doc-456");
        assertThat(event.getDocumentType()).isEqualTo("INVOICE");
        assertThat(event.getStatus()).isEqualTo("VALIDATED");
        assertThat(event.getSource()).isEqualTo("KAFKA");
        assertThat(event.getCorrelationId()).isNull();
    }

    @Test
    @DisplayName("Should handle unknown fields gracefully via @JsonIgnoreProperties")
    void shouldIgnoreUnknownFields() throws Exception {
        String json = """
            {
              "eventId": "123e4567-e89b-12d3-a456-426614174000",
              "occurredAt": "2024-01-01T00:00:00Z",
              "eventType": "DocumentReceivedTraceEvent",
              "version": 1,
              "documentId": "doc-789",
              "documentType": "ABBREVIATED_TAX_INVOICE",
              "documentNumber": "ATI-789",
              "correlationId": "corr-789",
              "status": "INVALID",
              "source": "API",
              "futureField": "should be ignored"
            }
            """;

        DocumentReceivedTraceEvent event = MAPPER.readValue(json, DocumentReceivedTraceEvent.class);

        assertThat(event.getDocumentId()).isEqualTo("doc-789");
        assertThat(event.getStatus()).isEqualTo("INVALID");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/notification-service-intake-stats
mvn clean test -Dtest=DocumentReceivedTraceEventTest -pl .
```
Expected: FAIL with `cannot find symbol: class DocumentReceivedTraceEvent`

- [ ] **Step 3: Create the DTO**

```java
package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Local DTO mirroring DocumentReceivedTraceEvent published by document-intake-service
 * to Kafka topic: trace.document.received
 *
 * Statuses: RECEIVED, VALIDATED, FORWARDED, INVALID
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentReceivedTraceEvent extends TraceEvent {

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("status")
    private final String status;

    @JsonCreator
    public DocumentReceivedTraceEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("documentNumber") String documentNumber,
        @JsonProperty("status") String status
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.status = status;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn clean test -Dtest=DocumentReceivedTraceEventTest -pl .
```
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add \
  src/main/java/com/wpanther/notification/application/port/in/event/DocumentReceivedTraceEvent.java \
  src/test/java/com/wpanther/notification/application/port/in/event/DocumentReceivedTraceEventTest.java
git commit -m "feat: add DocumentReceivedTraceEvent local DTO with JSON round-trip test"
```

---

## Task 5: JPA persistence layer + test

**Files:**
- Create: `src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/DocumentIntakeStatEntity.java`
- Create: `src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/SpringDataDocumentIntakeStatRepository.java`
- Create: `src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/JpaDocumentIntakeStatRepository.java`
- Create: `src/test/java/com/wpanther/notification/infrastructure/adapter/out/persistence/JpaDocumentIntakeStatRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/wpanther/notification/infrastructure/adapter/out/persistence/JpaDocumentIntakeStatRepositoryTest.java`:
```java
package com.wpanther.notification.infrastructure.adapter.out.persistence;

import com.wpanther.notification.domain.model.DocumentIntakeStat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaDocumentIntakeStatRepository.class)
@ActiveProfiles("test")
@DisplayName("JpaDocumentIntakeStatRepository Tests")
class JpaDocumentIntakeStatRepositoryTest {

    @Autowired
    private JpaDocumentIntakeStatRepository repository;

    @Test
    @DisplayName("Should save and return DocumentIntakeStat")
    void shouldSaveAndReturn() {
        DocumentIntakeStat stat = buildStat("doc-1", "TAX_INVOICE", "RECEIVED");

        DocumentIntakeStat saved = repository.save(stat);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDocumentId()).isEqualTo("doc-1");
        assertThat(saved.getStatus()).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("Should return counts grouped by status")
    void shouldCountByStatus() {
        repository.save(buildStat("doc-1", "TAX_INVOICE", "RECEIVED"));
        repository.save(buildStat("doc-1", "TAX_INVOICE", "VALIDATED"));
        repository.save(buildStat("doc-1", "TAX_INVOICE", "FORWARDED"));
        repository.save(buildStat("doc-2", "INVOICE", "RECEIVED"));
        repository.save(buildStat("doc-3", "INVOICE", "INVALID"));

        Map<String, Long> counts = repository.countByStatus();

        assertThat(counts).containsEntry("RECEIVED", 2L);
        assertThat(counts).containsEntry("VALIDATED", 1L);
        assertThat(counts).containsEntry("FORWARDED", 1L);
        assertThat(counts).containsEntry("INVALID", 1L);
    }

    @Test
    @DisplayName("Should return counts grouped by document type")
    void shouldCountByDocumentType() {
        repository.save(buildStat("doc-1", "TAX_INVOICE", "RECEIVED"));
        repository.save(buildStat("doc-2", "TAX_INVOICE", "RECEIVED"));
        repository.save(buildStat("doc-3", "INVOICE", "RECEIVED"));

        Map<String, Long> counts = repository.countByDocumentType();

        assertThat(counts).containsEntry("TAX_INVOICE", 2L);
        assertThat(counts).containsEntry("INVOICE", 1L);
    }

    @Test
    @DisplayName("Should find stats by documentId ordered by occurredAt")
    void shouldFindByDocumentIdOrdered() {
        Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T10:01:00Z");
        Instant t3 = Instant.parse("2026-01-01T10:02:00Z");

        repository.save(buildStatAt("doc-A", "TAX_INVOICE", "FORWARDED", t3));
        repository.save(buildStatAt("doc-A", "TAX_INVOICE", "RECEIVED", t1));
        repository.save(buildStatAt("doc-A", "TAX_INVOICE", "VALIDATED", t2));
        repository.save(buildStatAt("doc-B", "TAX_INVOICE", "RECEIVED", t1));

        List<DocumentIntakeStat> results = repository.findByDocumentId("doc-A");

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getStatus()).isEqualTo("RECEIVED");
        assertThat(results.get(1).getStatus()).isEqualTo("VALIDATED");
        assertThat(results.get(2).getStatus()).isEqualTo("FORWARDED");
    }

    @Test
    @DisplayName("Should return empty list when documentId not found")
    void shouldReturnEmptyWhenNotFound() {
        List<DocumentIntakeStat> results = repository.findByDocumentId("no-such-doc");
        assertThat(results).isEmpty();
    }

    private DocumentIntakeStat buildStat(String documentId, String documentType, String status) {
        return buildStatAt(documentId, documentType, status, Instant.now());
    }

    private DocumentIntakeStat buildStatAt(String documentId, String documentType, String status, Instant occurredAt) {
        return DocumentIntakeStat.builder()
            .id(UUID.randomUUID())
            .documentId(documentId)
            .documentType(documentType)
            .documentNumber("TIV-001")
            .status(status)
            .source("API")
            .correlationId("corr-1")
            .occurredAt(occurredAt)
            .build();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn clean test -Dtest=JpaDocumentIntakeStatRepositoryTest -pl .
```
Expected: FAIL with `cannot find symbol: class JpaDocumentIntakeStatRepository`

- [ ] **Step 3: Create `DocumentIntakeStatEntity`**

> **Note:** Indexes are defined in both the V5 Flyway migration (Task 1) and here via `@Table(indexes=...)`. Flyway handles index creation in production; JPA handles it in test via H2's `ddl-auto: create-drop`. Both are needed — do not remove either.

`src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/DocumentIntakeStatEntity.java`:
```java
package com.wpanther.notification.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_intake_stats", indexes = {
    @Index(name = "idx_intake_stats_document_id",   columnList = "document_id"),
    @Index(name = "idx_intake_stats_status",         columnList = "status"),
    @Index(name = "idx_intake_stats_document_type",  columnList = "document_type"),
    @Index(name = "idx_intake_stats_occurred_at",    columnList = "occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentIntakeStatEntity {

    @Id
    @Column(columnDefinition = "UUID") // PostgreSQL-specific; H2 PG mode handles this
    private UUID id;

    @Column(name = "document_id", nullable = false, length = 255)
    private String documentId;

    @Column(name = "document_type", length = 100)
    private String documentType;

    @Column(name = "document_number", length = 255)
    private String documentNumber;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
```

- [ ] **Step 4: Create `SpringDataDocumentIntakeStatRepository`**

`src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/SpringDataDocumentIntakeStatRepository.java`:
```java
package com.wpanther.notification.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
interface SpringDataDocumentIntakeStatRepository extends JpaRepository<DocumentIntakeStatEntity, UUID> {

    @Query("SELECT e.status, COUNT(e) FROM DocumentIntakeStatEntity e GROUP BY e.status")
    List<Object[]> countGroupByStatus();

    @Query("SELECT e.documentType, COUNT(e) FROM DocumentIntakeStatEntity e GROUP BY e.documentType")
    List<Object[]> countGroupByDocumentType();

    List<DocumentIntakeStatEntity> findByDocumentIdOrderByOccurredAtAsc(String documentId);
}
```

- [ ] **Step 5: Create `JpaDocumentIntakeStatRepository` adapter**

`src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/JpaDocumentIntakeStatRepository.java`:
```java
package com.wpanther.notification.infrastructure.adapter.out.persistence;

import com.wpanther.notification.domain.model.DocumentIntakeStat;
import com.wpanther.notification.domain.repository.DocumentIntakeStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JpaDocumentIntakeStatRepository implements DocumentIntakeStatRepository {

    private final SpringDataDocumentIntakeStatRepository springDataRepo;

    @Override
    public DocumentIntakeStat save(DocumentIntakeStat stat) {
        DocumentIntakeStatEntity entity = toEntity(stat);
        DocumentIntakeStatEntity saved = springDataRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Map<String, Long> countByStatus() {
        return springDataRepo.countGroupByStatus().stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> (Long) row[1]
            ));
    }

    @Override
    public Map<String, Long> countByDocumentType() {
        return springDataRepo.countGroupByDocumentType().stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> (Long) row[1]
            ));
    }

    @Override
    public List<DocumentIntakeStat> findByDocumentId(String documentId) {
        return springDataRepo.findByDocumentIdOrderByOccurredAtAsc(documentId).stream()
            .map(this::toDomain)
            .toList();
    }

    private DocumentIntakeStatEntity toEntity(DocumentIntakeStat stat) {
        return DocumentIntakeStatEntity.builder()
            .id(stat.getId())
            .documentId(stat.getDocumentId())
            .documentType(stat.getDocumentType())
            .documentNumber(stat.getDocumentNumber())
            .status(stat.getStatus())
            .source(stat.getSource())
            .correlationId(stat.getCorrelationId())
            .occurredAt(stat.getOccurredAt())
            .build();
    }

    private DocumentIntakeStat toDomain(DocumentIntakeStatEntity entity) {
        return DocumentIntakeStat.builder()
            .id(entity.getId())
            .documentId(entity.getDocumentId())
            .documentType(entity.getDocumentType())
            .documentNumber(entity.getDocumentNumber())
            .status(entity.getStatus())
            .source(entity.getSource())
            .correlationId(entity.getCorrelationId())
            .occurredAt(entity.getOccurredAt())
            .build();
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
mvn clean test -Dtest=JpaDocumentIntakeStatRepositoryTest -pl .
```
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add \
  src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/DocumentIntakeStatEntity.java \
  src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/SpringDataDocumentIntakeStatRepository.java \
  src/main/java/com/wpanther/notification/infrastructure/adapter/out/persistence/JpaDocumentIntakeStatRepository.java \
  src/test/java/com/wpanther/notification/infrastructure/adapter/out/persistence/JpaDocumentIntakeStatRepositoryTest.java
git commit -m "feat: add DocumentIntakeStat persistence layer"
```

---

## Task 6: `DocumentIntakeStatUseCase` + `NotificationService` + test

**Files:**
- Create: `src/main/java/com/wpanther/notification/application/usecase/DocumentIntakeStatUseCase.java`
- Modify: `src/main/java/com/wpanther/notification/application/service/NotificationService.java`
- Modify: `src/test/java/com/wpanther/notification/application/service/NotificationServiceTest.java`

- [ ] **Step 1: Create `DocumentIntakeStatUseCase`**

```java
package com.wpanther.notification.application.usecase;

import com.wpanther.notification.application.port.in.event.DocumentReceivedTraceEvent;

/**
 * Input port: persist document intake status events as statistics rows.
 * Implemented by NotificationService.
 */
public interface DocumentIntakeStatUseCase {

    void handleIntakeStat(DocumentReceivedTraceEvent event);
}
```

- [ ] **Step 2: Write failing test for `handleIntakeStat` in `NotificationServiceTest`**

Open `src/test/java/com/wpanther/notification/application/service/NotificationServiceTest.java`.

Add the following import and mock at the top of the class (alongside the existing mocks):
```java
import com.wpanther.notification.application.port.in.event.DocumentReceivedTraceEvent;
import com.wpanther.notification.domain.model.DocumentIntakeStat;
import com.wpanther.notification.domain.repository.DocumentIntakeStatRepository;
```

Add the new mock field inside the class (after the existing `@Mock` fields):
```java
@Mock
private DocumentIntakeStatRepository documentIntakeStatRepository;
```

Add the following test method at the bottom of the class (before the closing `}`):
```java
@Test
@DisplayName("handleIntakeStat should map event fields to DocumentIntakeStat and save")
void handleIntakeStat_shouldMapAndSave() {
    // Arrange — build a fake event using the Jackson deserialization constructor
    DocumentReceivedTraceEvent event = new DocumentReceivedTraceEvent(
        java.util.UUID.randomUUID(),         // eventId
        java.time.Instant.now(),             // occurredAt
        "DOCUMENT_RECEIVED_TRACE",           // eventType
        1,                                   // version
        "doc-001",                           // sagaId (maps to documentId)
        "corr-001",                          // correlationId
        "document-intake-service",           // source
        "RECEIVED",                          // traceType (maps to status)
        null,                                // context
        "doc-001",                           // documentId
        "TAX_INVOICE",                       // documentType
        "TIV-2024-001",                      // documentNumber
        "RECEIVED"                           // status
    );

    ArgumentCaptor<DocumentIntakeStat> captor = ArgumentCaptor.forClass(DocumentIntakeStat.class);
    when(documentIntakeStatRepository.save(any(DocumentIntakeStat.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    // Act
    notificationService.handleIntakeStat(event);

    // Assert
    verify(documentIntakeStatRepository).save(captor.capture());
    DocumentIntakeStat saved = captor.getValue();
    assertThat(saved.getDocumentId()).isEqualTo("doc-001");
    assertThat(saved.getDocumentType()).isEqualTo("TAX_INVOICE");
    assertThat(saved.getDocumentNumber()).isEqualTo("TIV-2024-001");
    assertThat(saved.getStatus()).isEqualTo("RECEIVED");
    assertThat(saved.getSource()).isEqualTo("document-intake-service");
    assertThat(saved.getCorrelationId()).isEqualTo("corr-001");
    assertThat(saved.getOccurredAt()).isNotNull();
    assertThat(saved.getId()).isNotNull();
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
mvn clean test -Dtest=NotificationServiceTest#handleIntakeStat_shouldMapAndSave -pl .
```
Expected: FAIL — `notificationService` does not yet implement `DocumentIntakeStatUseCase`

- [ ] **Step 4: Update `NotificationService` to implement `DocumentIntakeStatUseCase`**

Open `src/main/java/com/wpanther/notification/application/service/NotificationService.java`.

**4a.** Add `DocumentIntakeStatRepository` as a new final field (after `dispatcherService`):
```java
private final DocumentIntakeStatRepository documentIntakeStatRepository;
```

**4b.** Add the import:
```java
import com.wpanther.notification.application.usecase.DocumentIntakeStatUseCase;
import com.wpanther.notification.application.port.in.event.DocumentReceivedTraceEvent;
import com.wpanther.notification.domain.model.DocumentIntakeStat;
import com.wpanther.notification.domain.repository.DocumentIntakeStatRepository;
```

**4c.** Add `DocumentIntakeStatUseCase` to the `implements` list on the class declaration:
```java
public class NotificationService
        implements SendNotificationUseCase,
                   QueryNotificationUseCase,
                   RetryNotificationUseCase,
                   ProcessingEventUseCase,
                   DocumentReceivedEventUseCase,
                   SagaEventUseCase,
                   DocumentIntakeStatUseCase {
```

**4d.** Add the `handleIntakeStat` implementation at the bottom of the `// ── DocumentReceivedEventUseCase` section (or start a new section):
```java
// ── DocumentIntakeStatUseCase ─────────────────────────────────────────────────────────

@Override
public void handleIntakeStat(DocumentReceivedTraceEvent event) {
    log.info("Persisting intake stat: documentId={}, status={}, documentType={}",
        event.getDocumentId(), event.getStatus(), event.getDocumentType());

    DocumentIntakeStat stat = DocumentIntakeStat.builder()
        .id(java.util.UUID.randomUUID())
        .documentId(event.getDocumentId())
        .documentType(event.getDocumentType())
        .documentNumber(event.getDocumentNumber())
        .status(event.getStatus())
        .source(event.getSource())
        .correlationId(event.getCorrelationId())
        .occurredAt(event.getOccurredAt() != null ? event.getOccurredAt() : java.time.Instant.now())
        .build();

    documentIntakeStatRepository.save(stat);
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn clean test -Dtest=NotificationServiceTest -pl .
```
Expected: all tests in `NotificationServiceTest` pass

- [ ] **Step 6: Commit**

```bash
git add \
  src/main/java/com/wpanther/notification/application/usecase/DocumentIntakeStatUseCase.java \
  src/main/java/com/wpanther/notification/application/service/NotificationService.java \
  src/test/java/com/wpanther/notification/application/service/NotificationServiceTest.java
git commit -m "feat: implement DocumentIntakeStatUseCase in NotificationService"
```

---

## Task 7: Config wiring

**Files:**
- Modify: `src/main/java/com/wpanther/notification/infrastructure/config/KafkaTopicsConfig.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`

> **Note:** `documentReceived` is kept in the record here because Route 7 still references `topics.documentReceived()`. It will be removed in Task 10 alongside Route 7.

- [ ] **Step 1: Update `KafkaTopicsConfig`**

Open `src/main/java/com/wpanther/notification/infrastructure/config/KafkaTopicsConfig.java`.

Add `traceDocumentReceived` as a new field (keep `documentReceived` for now — removed in Task 10):
```java
@ConfigurationProperties(prefix = "kafka.topics")
public record KafkaTopicsConfig(
    String invoiceProcessed,
    String taxinvoiceProcessed,
    String pdfGenerated,
    String pdfGeneratedTaxInvoice,
    String pdfSigned,
    String xmlSigned,
    String ebmsSent,
    String notificationDlq,
    String documentReceived,
    String traceDocumentReceived,
    String taxInvoiceReceived,
    String invoiceReceived,
    String receiptReceived,
    String debitCreditNoteReceived,
    String cancellationReceived,
    String abbreviatedReceived,
    String sagaLifecycleStarted,
    String sagaLifecycleStepCompleted,
    String sagaLifecycleCompleted,
    String sagaLifecycleFailed
) {}
```

- [ ] **Step 2: Update `application.yml`**

In `src/main/resources/application.yml`, under `kafka.topics:`, add the new topic (keep `document-received` for now — removed in Task 10):
```yaml
    trace-document-received: trace.document.received
```

- [ ] **Step 3: Update `application-test.yml`**

In `src/test/resources/application-test.yml`, add the following entries under the `kafka.topics:` block (add any that are missing; keep `document-received` for now — removed in Task 10):
```yaml
kafka:
  topics:
    invoice-processed: invoice.processed
    taxinvoice-processed: taxinvoice.processed
    pdf-generated: pdf.generated.invoice
    pdf-generated-tax-invoice: pdf.generated.tax-invoice
    pdf-signed: pdf.signed
    xml-signed: xml.signed
    ebms-sent: ebms.sent
    notification-dlq: notification.dlq
    document-received: document.received
    trace-document-received: trace.document.received
    tax-invoice-received: document.received.tax-invoice
    invoice-received: document.received.invoice
    receipt-received: document.received.receipt
    debit-credit-note-received: document.received.debit-credit-note
    cancellation-received: document.received.cancellation
    abbreviated-received: document.received.abbreviated
    saga-lifecycle-started: saga.lifecycle.started
    saga-lifecycle-step-completed: saga.lifecycle.step-completed
    saga-lifecycle-completed: saga.lifecycle.completed
    saga-lifecycle-failed: saga.lifecycle.failed
```

- [ ] **Step 4: Run the full test suite to verify nothing broke**

```bash
mvn clean test -pl .
```
Expected: all existing tests pass

- [ ] **Step 5: Commit**

```bash
git add \
  src/main/java/com/wpanther/notification/infrastructure/config/KafkaTopicsConfig.java \
  src/main/resources/application.yml \
  src/test/resources/application-test.yml
git commit -m "feat: add trace-document-received topic to Kafka config"
```

---

## Task 8: Add Route 19 to `NotificationEventRoutes`

**Files:**
- Modify: `src/main/java/com/wpanther/notification/infrastructure/adapter/in/kafka/NotificationEventRoutes.java`

- [ ] **Step 1: Add `DocumentIntakeStatUseCase` as a constructor parameter**

Open `NotificationEventRoutes.java`.

Add the new field after `sagaEventUseCase`:
```java
private final DocumentIntakeStatUseCase documentIntakeStatUseCase;
```

Add import:
```java
import com.wpanther.notification.application.usecase.DocumentIntakeStatUseCase;
import com.wpanther.notification.application.port.in.event.DocumentReceivedTraceEvent;
```

Update the constructor to include the new parameter (add after `sagaEventUseCase`):
```java
DocumentIntakeStatUseCase documentIntakeStatUseCase,
```
And in the constructor body:
```java
this.documentIntakeStatUseCase = documentIntakeStatUseCase;
```

- [ ] **Step 2: Add Route 19 in `configure()`**

In `NotificationEventRoutes.configure()`, add Route 19 after Route 18 (after the saga failed route):

```java
// Route 19: Document Intake Trace Events (all statuses: RECEIVED, VALIDATED, FORWARDED, INVALID)
from("kafka:" + topics.traceDocumentReceived() + kafkaOptions)
    .routeId("notification-trace-document-received")
    .log("Received DocumentReceivedTraceEvent from Kafka")
    .choice()
        .when(exchange -> !notificationEnabled)
            .log("Notifications disabled, skipping trace event")
            .stop()
    .end()
    .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedTraceEvent.class)
    .process(this::handleIntakeStat)
    .log("Persisted intake stat: documentId=${header.documentId}, status=${header.status}");
```

- [ ] **Step 3: Add `handleIntakeStat` private handler method**

Add after `handleSagaFailed`:
```java
private void handleIntakeStat(Exchange exchange) {
    DocumentReceivedTraceEvent event = exchange.getIn().getBody(DocumentReceivedTraceEvent.class);
    documentIntakeStatUseCase.handleIntakeStat(event);
    exchange.getIn().setHeader("documentId", event.getDocumentId());
    exchange.getIn().setHeader("status", event.getStatus());
}
```

- [ ] **Step 4: Run the full test suite**

```bash
mvn clean test -pl .
```
Expected: all tests pass

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wpanther/notification/infrastructure/adapter/in/kafka/NotificationEventRoutes.java
git commit -m "feat: add Route 19 consuming trace.document.received"
```

---

## Task 9: REST endpoint + test

**Files:**
- Create: `src/main/java/com/wpanther/notification/application/dto/DocumentIntakeStatsResponse.java`
- Modify: `src/main/java/com/wpanther/notification/infrastructure/adapter/in/rest/NotificationController.java`
- Modify: `src/test/java/com/wpanther/notification/infrastructure/adapter/in/rest/NotificationControllerTest.java`

- [ ] **Step 1: Create `DocumentIntakeStatsResponse`**

```java
package com.wpanther.notification.application.dto;

import java.util.Map;

/**
 * Response DTO for GET /api/v1/notifications/statistics/intake
 */
public record DocumentIntakeStatsResponse(
    Map<String, Long> statusCounts,
    Map<String, Long> documentTypeCounts
) {}
```

- [ ] **Step 2: Write failing tests in `NotificationControllerTest`**

Open `src/test/java/com/wpanther/notification/infrastructure/adapter/in/rest/NotificationControllerTest.java`.

Add imports at the top:
```java
import com.wpanther.notification.application.dto.DocumentIntakeStatsResponse;
import com.wpanther.notification.application.port.in.event.DocumentReceivedTraceEvent;
import com.wpanther.notification.application.usecase.DocumentIntakeStatUseCase;
import com.wpanther.notification.domain.model.DocumentIntakeStat;
```

Add mock bean after the existing `@MockBean` fields:
```java
@MockBean
private DocumentIntakeStatUseCase documentIntakeStatUseCase;
```

Add two new test methods at the end of the class:
```java
// ── GET /api/v1/notifications/statistics/intake Tests ────────────────────────────────

@Test
@DisplayName("GET /api/v1/notifications/statistics/intake should return aggregate counts")
void testGetIntakeStatistics() throws Exception {
    DocumentIntakeStatsResponse response = new DocumentIntakeStatsResponse(
        Map.of("RECEIVED", 10L, "VALIDATED", 9L, "FORWARDED", 8L, "INVALID", 1L),
        Map.of("TAX_INVOICE", 7L, "INVOICE", 3L)
    );
    when(documentIntakeStatUseCase.getIntakeStats()).thenReturn(response);

    mockMvc.perform(get("/api/v1/notifications/statistics/intake"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statusCounts.RECEIVED").value(10))
        .andExpect(jsonPath("$.statusCounts.INVALID").value(1))
        .andExpect(jsonPath("$.documentTypeCounts.TAX_INVOICE").value(7));

    verify(documentIntakeStatUseCase).getIntakeStats();
}

@Test
@DisplayName("GET /api/v1/notifications/statistics/intake?documentId=X should return stat list")
void testGetIntakeStatsByDocumentId() throws Exception {
    List<DocumentIntakeStat> stats = List.of(
        DocumentIntakeStat.builder()
            .id(UUID.randomUUID()).documentId("doc-001").status("RECEIVED")
            .documentType("TAX_INVOICE").occurredAt(java.time.Instant.now()).build(),
        DocumentIntakeStat.builder()
            .id(UUID.randomUUID()).documentId("doc-001").status("VALIDATED")
            .documentType("TAX_INVOICE").occurredAt(java.time.Instant.now()).build()
    );
    when(documentIntakeStatUseCase.getStatsByDocumentId("doc-001")).thenReturn(stats);

    mockMvc.perform(get("/api/v1/notifications/statistics/intake").param("documentId", "doc-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].status").value("RECEIVED"))
        .andExpect(jsonPath("$[1].status").value("VALIDATED"));

    verify(documentIntakeStatUseCase).getStatsByDocumentId("doc-001");
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
mvn clean test -Dtest=NotificationControllerTest -pl .
```
Expected: FAIL — `documentIntakeStatUseCase.getIntakeStats()` doesn't exist yet

- [ ] **Step 4: Update `DocumentIntakeStatUseCase` to add query methods**

Open `src/main/java/com/wpanther/notification/application/usecase/DocumentIntakeStatUseCase.java`.

Add two query methods (plus the required imports):
```java
package com.wpanther.notification.application.usecase;

import com.wpanther.notification.application.dto.DocumentIntakeStatsResponse;
import com.wpanther.notification.application.port.in.event.DocumentReceivedTraceEvent;
import com.wpanther.notification.domain.model.DocumentIntakeStat;

import java.util.List;

public interface DocumentIntakeStatUseCase {

    void handleIntakeStat(DocumentReceivedTraceEvent event);

    DocumentIntakeStatsResponse getIntakeStats();

    List<DocumentIntakeStat> getStatsByDocumentId(String documentId);
}
```

- [ ] **Step 5: Implement the two query methods in `NotificationService`**

Open `src/main/java/com/wpanther/notification/application/service/NotificationService.java`.

Add imports:
```java
import com.wpanther.notification.application.dto.DocumentIntakeStatsResponse;
```

Add the two implementations inside the `// ── DocumentIntakeStatUseCase` section:
```java
@Override
public DocumentIntakeStatsResponse getIntakeStats() {
    return new DocumentIntakeStatsResponse(
        documentIntakeStatRepository.countByStatus(),
        documentIntakeStatRepository.countByDocumentType()
    );
}

@Override
public List<DocumentIntakeStat> getStatsByDocumentId(String documentId) {
    return documentIntakeStatRepository.findByDocumentId(documentId);
}
```

- [ ] **Step 6: Add `DocumentIntakeStatUseCase` to `NotificationController`**

Open `src/main/java/com/wpanther/notification/infrastructure/adapter/in/rest/NotificationController.java`.

Add import:
```java
import com.wpanther.notification.application.dto.DocumentIntakeStatsResponse;
import com.wpanther.notification.application.usecase.DocumentIntakeStatUseCase;
import com.wpanther.notification.domain.model.DocumentIntakeStat;
```

Add field (after `retryNotificationUseCase`):
```java
private final DocumentIntakeStatUseCase documentIntakeStatUseCase;
```

Add the new endpoint method (after `getStatistics()`):
```java
/**
 * Get document intake statistics: counts by status and document type.
 * Optional ?documentId param returns per-document lifecycle history.
 */
@GetMapping("/statistics/intake")
public ResponseEntity<Object> getIntakeStatistics(
    @RequestParam(required = false) String documentId
) {
    if (documentId != null) {
        return ResponseEntity.ok(documentIntakeStatUseCase.getStatsByDocumentId(documentId));
    }
    return ResponseEntity.ok(documentIntakeStatUseCase.getIntakeStats());
}
```

- [ ] **Step 7: Run tests to verify they pass**

```bash
mvn clean test -Dtest=NotificationControllerTest -pl .
```
Expected: all tests pass

- [ ] **Step 8: Commit**

```bash
git add \
  src/main/java/com/wpanther/notification/application/dto/DocumentIntakeStatsResponse.java \
  src/main/java/com/wpanther/notification/application/usecase/DocumentIntakeStatUseCase.java \
  src/main/java/com/wpanther/notification/application/service/NotificationService.java \
  src/main/java/com/wpanther/notification/infrastructure/adapter/in/rest/NotificationController.java \
  src/test/java/com/wpanther/notification/infrastructure/adapter/in/rest/NotificationControllerTest.java
git commit -m "feat: add GET /statistics/intake endpoint"
```

---

## Task 10: Remove dead code

**Files:**
- Delete: `src/main/java/com/wpanther/notification/application/port/in/event/DocumentReceivedCountingEvent.java`
- Modify: `src/main/java/com/wpanther/notification/application/usecase/DocumentReceivedEventUseCase.java`
- Modify: `src/main/java/com/wpanther/notification/application/service/NotificationService.java`
- Modify: `src/main/java/com/wpanther/notification/infrastructure/adapter/in/kafka/NotificationEventRoutes.java`
- Modify: `src/main/java/com/wpanther/notification/infrastructure/config/KafkaTopicsConfig.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-test.yml`

- [ ] **Step 1: Remove Route 7 from `NotificationEventRoutes`**

Open `NotificationEventRoutes.java`.

Delete the entire Route 7 block (from `// Route 7: Document Received Counting Events` to the closing `.log(...)` line):
```java
// Route 7: Document Received Counting Events (before validation - all documents)
// This lightweight event tracks ALL received documents regardless of validation outcome
from("kafka:" + topics.documentReceived() + kafkaOptions)
    .routeId("notification-document-counting")
    .log("Received DocumentReceivedCountingEvent from Kafka")
    .choice()
        .when(exchange -> !notificationEnabled)
            .log("Notifications disabled, skipping counting event")
            .stop()
    .end()
    .unmarshal().json(JsonLibrary.Jackson, DocumentReceivedCountingEvent.class)
    .process(this::handleDocumentReceivedCounting)
    .log("Processed document counting event: documentId=${header.documentId}");
```

Also delete the `handleDocumentReceivedCounting` private method:
```java
private void handleDocumentReceivedCounting(Exchange exchange) {
    DocumentReceivedCountingEvent event = exchange.getIn().getBody(DocumentReceivedCountingEvent.class);
    documentReceivedEventUseCase.handleDocumentCounting(event);
    exchange.getIn().setHeader("documentId", event.getDocumentId());
}
```

Remove the `DocumentReceivedCountingEvent` import from the top of the file.

- [ ] **Step 2: Remove `handleDocumentCounting` from `DocumentReceivedEventUseCase`**

Replace the contents of `src/main/java/com/wpanther/notification/application/usecase/DocumentReceivedEventUseCase.java` with:
```java
package com.wpanther.notification.application.usecase;

import com.wpanther.notification.application.port.in.event.DocumentReceivedEvent;

/**
 * Input port: use case for handling document received events (type-specific statistics).
 * Implemented by NotificationService.
 */
public interface DocumentReceivedEventUseCase {

    void handleDocumentReceived(DocumentReceivedEvent event);
}
```

- [ ] **Step 3: Remove `handleDocumentCounting` from `NotificationService`**

Open `src/main/java/com/wpanther/notification/application/service/NotificationService.java`.

Remove the `handleDocumentCounting` override method (it just logs and is now dead):
```java
@Override
public void handleDocumentCounting(DocumentReceivedCountingEvent event) {
    log.info("Processing DocumentReceivedCountingEvent: documentId={}, correlationId={}",
        event.getDocumentId(), event.getCorrelationId());
    // Log only. Future: persist to database for total received count statistics.
}
```

Remove the `DocumentReceivedCountingEvent` import from the file.

- [ ] **Step 4: Delete `DocumentReceivedCountingEvent`**

```bash
rm src/main/java/com/wpanther/notification/application/port/in/event/DocumentReceivedCountingEvent.java
```

- [ ] **Step 5: Remove `documentReceived` from `KafkaTopicsConfig`**

Open `src/main/java/com/wpanther/notification/infrastructure/config/KafkaTopicsConfig.java`.

Remove `String documentReceived,` from the record declaration.

- [ ] **Step 6: Remove `document-received` from `application.yml`**

In `src/main/resources/application.yml`, under `kafka.topics:`, delete:
```yaml
    document-received: document.received
```

- [ ] **Step 7: Remove `document-received` from `application-test.yml`**

In `src/test/resources/application-test.yml`, under `kafka.topics:`, delete:
```yaml
    document-received: document.received
```

- [ ] **Step 8: Run the full test suite**

```bash
mvn clean test -pl .
```
Expected: all tests pass, no compilation errors

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "chore: remove dead Route 7, DocumentReceivedCountingEvent, and document-received config"
```

---

## Task 11: Final verification

- [ ] **Step 1: Run full test suite with coverage**

```bash
mvn verify -pl .
```
Expected: `BUILD SUCCESS`, JaCoCo 90% line coverage requirement met

- [ ] **Step 2: Confirm all new classes appear in coverage report**

Check `target/site/jacoco/index.html` (or review the verify output) to confirm:
- `DocumentIntakeStat` — covered
- `JpaDocumentIntakeStatRepository` — covered
- `NotificationService.handleIntakeStat` — covered
- `NotificationController` statistics/intake endpoint — covered

- [ ] **Step 3: Commit if any coverage fixes were needed, otherwise done**

```bash
git log --oneline -8
```
Expected output (in order):
```
chore: remove dead Route 7, DocumentReceivedCountingEvent, and document-received config
feat: add GET /statistics/intake endpoint
feat: add Route 19 consuming trace.document.received
feat: add trace-document-received topic to Kafka config
feat: implement DocumentIntakeStatUseCase in NotificationService
feat: add DocumentIntakeStat persistence layer
feat: add DocumentIntakeStatRepository domain interface
feat: add DocumentReceivedTraceEvent local DTO
feat: add DocumentIntakeStat domain model
feat: add V5 migration for document_intake_stats table
```
