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
