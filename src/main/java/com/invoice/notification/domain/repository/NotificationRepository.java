package com.invoice.notification.domain.repository;

import com.invoice.notification.domain.model.Notification;
import com.invoice.notification.domain.model.NotificationStatus;
import com.invoice.notification.domain.model.NotificationType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Notification aggregate
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    List<Notification> findByStatus(NotificationStatus status);

    List<Notification> findByInvoiceId(String invoiceId);

    List<Notification> findByInvoiceNumber(String invoiceNumber);

    List<Notification> findByRecipient(String recipient);

    List<Notification> findByType(NotificationType type);

    List<Notification> findFailedNotifications(int maxRetries);

    List<Notification> findPendingNotifications();

    List<Notification> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatus(NotificationStatus status);

    long countByType(NotificationType type);

    void deleteById(UUID id);
}
