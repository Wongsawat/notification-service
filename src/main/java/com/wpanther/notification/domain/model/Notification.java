package com.wpanther.notification.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Notification aggregate root
 * Represents a notification to be sent through various channels
 */
@Data
@Builder
public class Notification {

    private UUID id;
    private NotificationType type;
    private NotificationChannel channel;
    private NotificationStatus status;
    private String recipient;           // Email, phone, or webhook URL
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

    /**
     * Create new notification
     */
    public static Notification create(NotificationType type, NotificationChannel channel,
                                     String recipient, String subject, String body) {
        return Notification.builder()
            .id(UUID.randomUUID())
            .type(type)
            .channel(channel)
            .status(NotificationStatus.PENDING)
            .recipient(recipient)
            .subject(subject)
            .body(body)
            .metadata(new HashMap<>())
            .templateVariables(new HashMap<>())
            .createdAt(LocalDateTime.now())
            .retryCount(0)
            .build();
    }

    /**
     * Create notification from template
     */
    public static Notification createFromTemplate(NotificationType type, NotificationChannel channel,
                                                  String recipient, String templateName,
                                                  Map<String, Object> templateVariables) {
        return Notification.builder()
            .id(UUID.randomUUID())
            .type(type)
            .channel(channel)
            .status(NotificationStatus.PENDING)
            .recipient(recipient)
            .templateName(templateName)
            .templateVariables(new HashMap<>(templateVariables))
            .metadata(new HashMap<>())
            .createdAt(LocalDateTime.now())
            .retryCount(0)
            .build();
    }

    /**
     * Mark notification as sending
     */
    public void markSending() {
        if (this.status != NotificationStatus.PENDING && this.status != NotificationStatus.RETRYING) {
            throw new IllegalStateException("Can only start sending from PENDING or RETRYING status");
        }
        this.status = NotificationStatus.SENDING;
    }

    /**
     * Mark notification as sent
     */
    public void markSent() {
        if (this.status != NotificationStatus.SENDING) {
            throw new IllegalStateException("Can only mark as sent from SENDING status");
        }
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /**
     * Mark notification as failed
     */
    public void markFailed(String errorMessage) {
        if (this.status != NotificationStatus.SENDING) {
            throw new IllegalStateException("Can only mark as failed from SENDING status");
        }
        this.status = NotificationStatus.FAILED;
        this.failedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    /**
     * Prepare for retry
     */
    public void prepareRetry() {
        if (this.status != NotificationStatus.FAILED) {
            throw new IllegalStateException("Can only retry from FAILED status");
        }
        this.status = NotificationStatus.RETRYING;
        this.retryCount++;
    }

    /**
     * Check if can retry
     */
    public boolean canRetry(int maxRetries) {
        return this.status == NotificationStatus.FAILED && this.retryCount < maxRetries;
    }

    /**
     * Add metadata
     */
    public void addMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * Add template variable
     */
    public void addTemplateVariable(String key, Object value) {
        if (this.templateVariables == null) {
            this.templateVariables = new HashMap<>();
        }
        this.templateVariables.put(key, value);
    }

    /**
     * Check if notification uses template
     */
    public boolean usesTemplate() {
        return this.templateName != null && !this.templateName.isEmpty();
    }
}
