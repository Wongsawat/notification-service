package com.wpanther.notification.application.port.in.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class DocumentArchivedEvent extends TraceEvent {
    private static final String EVENT_TYPE = "document.archived";
    private static final String SOURCE = "document-storage-service";
    private static final String TRACE_TYPE = "DOCUMENT_ARCHIVED";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentNumber")
    private final String documentNumber;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("artifactType")
    private final String artifactType;

    @JsonProperty("fileName")
    private final String fileName;

    @JsonProperty("fileSize")
    private final long fileSize;

    @JsonProperty("storedUrl")
    private final String storedUrl;

    @JsonProperty("archivedAt")
    private final Instant archivedAt;

    public DocumentArchivedEvent(String documentId, String documentNumber,
                                   String documentType, String artifactType,
                                   String fileName, long fileSize, String storedUrl,
                                   Instant archivedAt,
                                   String sagaId, String correlationId) {
        super(sagaId, correlationId, SOURCE, TRACE_TYPE, null);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
        this.artifactType = artifactType;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.storedUrl = storedUrl;
        this.archivedAt = archivedAt;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @JsonCreator
    public DocumentArchivedEvent(
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
        @JsonProperty("documentNumber") String documentNumber,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("artifactType") String artifactType,
        @JsonProperty("fileName") String fileName,
        @JsonProperty("fileSize") long fileSize,
        @JsonProperty("storedUrl") String storedUrl,
        @JsonProperty("archivedAt") Instant archivedAt
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, correlationId, source, traceType, context);
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
        this.artifactType = artifactType;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.storedUrl = storedUrl;
        this.archivedAt = archivedAt;
    }
}
