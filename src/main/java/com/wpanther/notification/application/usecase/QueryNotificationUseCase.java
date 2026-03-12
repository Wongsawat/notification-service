package com.wpanther.notification.application.usecase;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Input port: use case for querying notification state.
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 */
public interface QueryNotificationUseCase {

    Optional<Notification> findById(UUID id);

    List<Notification> findByInvoiceId(String invoiceId);

    List<Notification> findByStatus(NotificationStatus status);

    Map<String, Long> getStatistics();
}
