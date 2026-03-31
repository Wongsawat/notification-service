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
