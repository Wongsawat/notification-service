package com.wpanther.notification.domain.repository;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain-owned outbound port for persisting Notification aggregates.
 * Infrastructure layer provides the implementation via JPA adapter.
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    List<Notification> findByStatus(NotificationStatus status, int limit);

    List<Notification> findByInvoiceId(String invoiceId, int limit);

    List<Notification> findByInvoiceNumber(String invoiceNumber, int limit);

    List<Notification> findByRecipient(String recipient, int limit);

    List<Notification> findByType(NotificationType type, int limit);

    List<Notification> findStaleSendingNotifications(Instant threshold, int limit);

    List<Notification> findFailedNotifications(int maxRetries, int limit);

    List<Notification> findPendingNotifications(int limit);

    List<Notification> findByCreatedAtBetween(Instant start, Instant end, int limit);

    long countByStatus(NotificationStatus status);

    long countByType(NotificationType type);

    void deleteById(UUID id);
}
