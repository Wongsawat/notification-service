package com.wpanther.notification.application.port.out;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port: persistence contract for the Notification aggregate.
 * Implemented by {@link com.wpanther.notification.adapter.out.persistence.NotificationRepositoryAdapter}.
 */
public interface NotificationRepositoryPort {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    List<Notification> findByStatus(NotificationStatus status);

    List<Notification> findByInvoiceId(String invoiceId);

    List<Notification> findByInvoiceNumber(String invoiceNumber);

    List<Notification> findByRecipient(String recipient);

    List<Notification> findByType(NotificationType type);

    List<Notification> findStaleSendingNotifications(LocalDateTime threshold, int limit);

    List<Notification> findFailedNotifications(int maxRetries, int limit);

    List<Notification> findPendingNotifications(int limit);

    List<Notification> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatus(NotificationStatus status);

    long countByType(NotificationType type);

    void deleteById(UUID id);
}
