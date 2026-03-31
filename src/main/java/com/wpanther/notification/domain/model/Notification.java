package com.wpanther.notification.domain.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Notification aggregate root.
 *
 * <p>Pure-Java domain model: no framework or annotation-processor dependencies.
 * State transitions are the only way to mutate status/retryCount/timestamps —
 * no public setters exist for those fields.  Only contextual fields that must be
 * populated after construction ({@code subject}, {@code documentId},
 * {@code documentNumber}, {@code correlationId}) have explicit setters.</p>
 *
 * <p>Equality is based on aggregate identity ({@code id}), consistent with DDD
 * aggregate-root semantics.</p>
 */
public class Notification {

    private final UUID id;
    private final NotificationType type;
    private final NotificationChannel channel;
    private NotificationStatus status;
    private final String recipient;
    private String subject;
    private final String body;
    private Map<String, Object> metadata;
    private final String templateName;
    private Map<String, Object> templateVariables;
    private String documentId;
    private String documentNumber;
    private String correlationId;
    private final Instant createdAt;
    private Instant sentAt;
    private Instant failedAt;
    private int retryCount;
    private String errorMessage;

    private Notification(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.channel = builder.channel;
        this.status = builder.status;
        this.recipient = builder.recipient;
        this.subject = builder.subject;
        this.body = builder.body;
        this.metadata = builder.metadata != null ? builder.metadata : new HashMap<>();
        this.templateName = builder.templateName;
        this.templateVariables = builder.templateVariables != null ? builder.templateVariables : new HashMap<>();
        this.documentId = builder.documentId;
        this.documentNumber = builder.documentNumber;
        this.correlationId = builder.correlationId;
        this.createdAt = builder.createdAt;
        this.sentAt = builder.sentAt;
        this.failedAt = builder.failedAt;
        this.retryCount = builder.retryCount;
        this.errorMessage = builder.errorMessage;
    }

    // ── Getters ───────────────────────────────────────────────────────────────────────────

    public UUID getId()                                  { return id; }
    public NotificationType getType()                    { return type; }
    public NotificationChannel getChannel()              { return channel; }
    public NotificationStatus getStatus()                { return status; }
    public String getRecipient()                         { return recipient; }
    public String getSubject()                           { return subject; }
    public String getBody()                              { return body; }
    public Map<String, Object> getMetadata()             { return metadata; }
    public String getTemplateName()                      { return templateName; }
    public Map<String, Object> getTemplateVariables()    { return templateVariables; }
    public String getDocumentId()                        { return documentId; }
    public String getDocumentNumber()                    { return documentNumber; }
    public String getCorrelationId()                     { return correlationId; }
    public Instant getCreatedAt()                        { return createdAt; }
    public Instant getSentAt()                           { return sentAt; }
    public Instant getFailedAt()                         { return failedAt; }
    public int getRetryCount()                           { return retryCount; }
    public String getErrorMessage()                      { return errorMessage; }

    // ── Permitted setters (contextual metadata only — NOT state-machine fields) ──────────

    /** Sets the notification subject. May be called after template-based construction. */
    public void setSubject(String subject)               { this.subject = subject; }

    /** Sets the associated document ID for correlation and querying. */
    public void setDocumentId(String documentId)         { this.documentId = documentId; }

    /** Sets the human-readable document number for display in notifications. */
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }

    /** Sets the saga/trace correlation ID propagated from upstream events. */
    public void setCorrelationId(String correlationId)   { this.correlationId = correlationId; }

    // ── Domain invariant helpers ──────────────────────────────────────────────────────────

    private static void requireNonNull(Object value, String paramName) {
        if (value == null) {
            throw new IllegalArgumentException(paramName + " is required");
        }
    }

    private static void requireNonBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " is required");
        }
    }

    // ── Factory methods ───────────────────────────────────────────────────────────────────

    /**
     * Creates a notification with a direct subject and body.
     *
     * @throws IllegalArgumentException if any required parameter is null or blank
     */
    public static Notification create(NotificationType type, NotificationChannel channel,
                                      String recipient, String subject, String body) {
        requireNonNull(type, "type");
        requireNonNull(channel, "channel");
        requireNonBlank(recipient, "recipient");
        requireNonBlank(subject, "subject");
        // body may be null for template-based notifications

        return new Builder()
                .id(UUID.randomUUID())
                .type(type)
                .channel(channel)
                .status(NotificationStatus.PENDING)
                .recipient(recipient)
                .subject(subject)
                .body(body)
                .metadata(new HashMap<>())
                .templateVariables(new HashMap<>())
                .createdAt(Instant.now())
                .retryCount(0)
                .build();
    }

    /**
     * Creates a notification backed by a Thymeleaf template.
     *
     * @throws IllegalArgumentException if any required parameter is null or blank
     */
    public static Notification createFromTemplate(NotificationType type, NotificationChannel channel,
                                                  String recipient, String templateName,
                                                  Map<String, Object> templateVariables) {
        requireNonNull(type, "type");
        requireNonNull(channel, "channel");
        requireNonBlank(recipient, "recipient");
        requireNonBlank(templateName, "templateName");
        requireNonNull(templateVariables, "templateVariables");

        return new Builder()
                .id(UUID.randomUUID())
                .type(type)
                .channel(channel)
                .status(NotificationStatus.PENDING)
                .recipient(recipient)
                .templateName(templateName)
                .templateVariables(new HashMap<>(templateVariables))
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .retryCount(0)
                .build();
    }

    // ── State machine ─────────────────────────────────────────────────────────────────────

    /**
     * Transitions from PENDING or RETRYING → SENDING.
     */
    public void markSending() {
        if (this.status != NotificationStatus.PENDING && this.status != NotificationStatus.RETRYING) {
            throw new IllegalStateException("Can only start sending from PENDING or RETRYING status");
        }
        this.status = NotificationStatus.SENDING;
    }

    /**
     * Transitions from SENDING → SENT. Clears any previous error message.
     */
    public void markSent() {
        if (this.status != NotificationStatus.SENDING) {
            throw new IllegalStateException("Can only mark as sent from SENDING status");
        }
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.errorMessage = null;
    }

    /**
     * Transitions from SENDING → FAILED. Records the error message and failure time.
     */
    public void markFailed(String errorMessage) {
        if (this.status != NotificationStatus.SENDING) {
            throw new IllegalStateException("Can only mark as failed from SENDING status");
        }
        this.status = NotificationStatus.FAILED;
        this.failedAt = Instant.now();
        this.errorMessage = errorMessage;
    }

    /**
     * Transitions from FAILED → RETRYING. Increments the retry counter.
     */
    public void prepareRetry() {
        if (this.status != NotificationStatus.FAILED) {
            throw new IllegalStateException("Can only retry from FAILED status");
        }
        this.status = NotificationStatus.RETRYING;
        this.retryCount++;
    }

    /** Returns {@code true} if this notification is eligible for another delivery attempt. */
    public boolean canRetry(int maxRetries) {
        return this.status == NotificationStatus.FAILED && this.retryCount < maxRetries;
    }

    // ── Metadata helpers ──────────────────────────────────────────────────────────────────

    /** Adds a metadata entry. Initialises the map if it has not been set. */
    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /** Adds a template variable. Initialises the map if it has not been set. */
    public void addTemplateVariable(String key, Object value) {
        if (this.templateVariables == null) {
            this.templateVariables = new HashMap<>();
        }
        this.templateVariables.put(key, value);
    }

    /** Returns {@code true} if this notification is template-driven. */
    public boolean usesTemplate() {
        return this.templateName != null && !this.templateName.isEmpty();
    }

    // ── Aggregate identity ────────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notification that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Notification{id=" + id + ", type=" + type + ", channel=" + channel
                + ", status=" + status + ", recipient='" + recipient + "'}";
    }

    // ── Builder (used by persistence adapter for reconstruction and by tests) ─────────────

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing {@link Notification} instances.
     *
     * <p>Intended for two use cases only:
     * <ol>
     *   <li>Reconstruction from the persistence layer (all fields may be set)</li>
     *   <li>Test setup (direct state injection without triggering transition guards)</li>
     * </ol>
     * Application code should use the factory methods {@link #create} or
     * {@link #createFromTemplate} instead.</p>
     */
    public static final class Builder {

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
        private String documentId;
        private String documentNumber;
        private String correlationId;
        private Instant createdAt;
        private Instant sentAt;
        private Instant failedAt;
        private int retryCount;
        private String errorMessage;

        private Builder() {}

        public Builder id(UUID id)                                          { this.id = id; return this; }
        public Builder type(NotificationType type)                          { this.type = type; return this; }
        public Builder channel(NotificationChannel channel)                 { this.channel = channel; return this; }
        public Builder status(NotificationStatus status)                    { this.status = status; return this; }
        public Builder recipient(String recipient)                          { this.recipient = recipient; return this; }
        public Builder subject(String subject)                              { this.subject = subject; return this; }
        public Builder body(String body)                                    { this.body = body; return this; }
        public Builder metadata(Map<String, Object> metadata)               { this.metadata = metadata; return this; }
        public Builder templateName(String templateName)                    { this.templateName = templateName; return this; }
        public Builder templateVariables(Map<String, Object> vars)          { this.templateVariables = vars; return this; }
        public Builder documentId(String documentId)                          { this.documentId = documentId; return this; }
        public Builder documentNumber(String documentNumber)                  { this.documentNumber = documentNumber; return this; }
        public Builder correlationId(String correlationId)                  { this.correlationId = correlationId; return this; }
        public Builder createdAt(Instant createdAt)                          { this.createdAt = createdAt; return this; }
        public Builder sentAt(Instant sentAt)                               { this.sentAt = sentAt; return this; }
        public Builder failedAt(Instant failedAt)                           { this.failedAt = failedAt; return this; }
        public Builder retryCount(int retryCount)                           { this.retryCount = retryCount; return this; }
        public Builder errorMessage(String errorMessage)                    { this.errorMessage = errorMessage; return this; }

        public Notification build() {
            return new Notification(this);
        }
    }
}
