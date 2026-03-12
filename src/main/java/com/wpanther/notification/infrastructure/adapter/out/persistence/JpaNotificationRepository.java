package com.wpanther.notification.infrastructure.adapter.out.persistence;

import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for NotificationEntity.
 * Provides CRUD operations and custom queries.
 */
@Repository
public interface JpaNotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByStatus(NotificationStatus status);

    List<NotificationEntity> findByInvoiceId(String invoiceId);

    List<NotificationEntity> findByInvoiceNumber(String invoiceNumber);

    List<NotificationEntity> findByRecipient(String recipient);

    List<NotificationEntity> findByType(NotificationType type);

    @Query("SELECT n FROM NotificationEntity n WHERE n.status = 'SENDING' AND n.createdAt < :threshold ORDER BY n.createdAt ASC")
    List<NotificationEntity> findStaleSendingNotifications(@Param("threshold") LocalDateTime threshold, Pageable pageable);

    @Query("SELECT n FROM NotificationEntity n WHERE n.status = 'FAILED' AND n.retryCount < :maxRetries")
    List<NotificationEntity> findFailedNotifications(@Param("maxRetries") int maxRetries, Pageable pageable);

    @Query("SELECT n FROM NotificationEntity n WHERE n.status = 'PENDING' ORDER BY n.createdAt ASC")
    List<NotificationEntity> findPendingNotifications(Pageable pageable);

    List<NotificationEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatus(NotificationStatus status);

    long countByType(NotificationType type);
}
