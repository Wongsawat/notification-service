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
