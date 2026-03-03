package com.wpanther.notification.adapter.out.persistence;

import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for Notification aggregate.
 * Maps to notifications table in notification_db.
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_invoice_id", columnList = "invoice_id"),
    @Index(name = "idx_recipient", columnList = "recipient"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_type", columnList = "type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false, length = 500)
    private String recipient;

    @Column(length = 500)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> metadata;

    @Column(name = "template_name", length = 100)
    private String templateName;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "template_variables", columnDefinition = "TEXT")
    private Map<String, Object> templateVariables;

    @Column(name = "invoice_id", length = 100)
    private String invoiceId;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
