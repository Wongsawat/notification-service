package com.wpanther.notification.infrastructure.adapter.out.persistence;

import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for NotificationEntity.
 * Provides CRUD operations and custom queries.
 */
@Repository
public interface JpaNotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByStatus(NotificationStatus status, Pageable pageable);

    List<NotificationEntity> findByDocumentId(String documentId, Pageable pageable);

    List<NotificationEntity> findByDocumentNumber(String documentNumber, Pageable pageable);

    List<NotificationEntity> findByRecipient(String recipient, Pageable pageable);

    List<NotificationEntity> findByType(NotificationType type, Pageable pageable);

    @Query("SELECT n FROM NotificationEntity n WHERE n.status = 'SENDING' AND n.createdAt < :threshold ORDER BY n.createdAt ASC")
    List<NotificationEntity> findStaleSendingNotifications(@Param("threshold") Instant threshold, Pageable pageable);

    @Query("SELECT n FROM NotificationEntity n WHERE n.status = 'FAILED' AND n.retryCount < :maxRetries")
    List<NotificationEntity> findFailedNotifications(@Param("maxRetries") int maxRetries, Pageable pageable);

    @Query("SELECT n FROM NotificationEntity n WHERE n.status = 'PENDING' ORDER BY n.createdAt ASC")
    List<NotificationEntity> findPendingNotifications(Pageable pageable);

    List<NotificationEntity> findByCreatedAtBetween(Instant start, Instant end, Pageable pageable);

    long countByStatus(NotificationStatus status);

    long countByType(NotificationType type);
}
