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
    @Column(columnDefinition = "UUID")
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
