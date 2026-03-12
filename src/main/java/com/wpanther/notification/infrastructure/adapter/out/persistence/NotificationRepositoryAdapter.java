package com.wpanther.notification.infrastructure.adapter.out.persistence;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter implementation of NotificationRepository using JPA.
 * Bridges between domain Notification model and JPA NotificationEntity.
 */
@Component
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final JpaNotificationRepository jpaRepository;

    @Override
    public Notification save(Notification notification) {
        NotificationEntity entity = toEntity(notification);
        NotificationEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Notification> findByStatus(NotificationStatus status) {
        return jpaRepository.findByStatus(status).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Notification> findByInvoiceId(String invoiceId) {
        return jpaRepository.findByInvoiceId(invoiceId).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Notification> findByInvoiceNumber(String invoiceNumber) {
        return jpaRepository.findByInvoiceNumber(invoiceNumber).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Notification> findByRecipient(String recipient) {
        return jpaRepository.findByRecipient(recipient).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Notification> findByType(NotificationType type) {
        return jpaRepository.findByType(type).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Notification> findStaleSendingNotifications(LocalDateTime threshold, int limit) {
        return jpaRepository.findStaleSendingNotifications(threshold, PageRequest.of(0, limit)).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Notification> findFailedNotifications(int maxRetries, int limit) {
        return jpaRepository.findFailedNotifications(maxRetries, PageRequest.of(0, limit)).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Notification> findPendingNotifications(int limit) {
        return jpaRepository.findPendingNotifications(PageRequest.of(0, limit)).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<Notification> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findByCreatedAtBetween(start, end).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public long countByStatus(NotificationStatus status) {
        return jpaRepository.countByStatus(status);
    }

    @Override
    public long countByType(NotificationType type) {
        return jpaRepository.countByType(type);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    /**
     * Convert domain model to entity
     */
    private NotificationEntity toEntity(Notification notification) {
        return NotificationEntity.builder()
            .id(notification.getId())
            .type(notification.getType())
            .channel(notification.getChannel())
            .status(notification.getStatus())
            .recipient(notification.getRecipient())
            .subject(notification.getSubject())
            .body(notification.getBody())
            .metadata(notification.getMetadata())
            .templateName(notification.getTemplateName())
            .templateVariables(notification.getTemplateVariables())
            .invoiceId(notification.getInvoiceId())
            .invoiceNumber(notification.getInvoiceNumber())
            .correlationId(notification.getCorrelationId())
            .createdAt(notification.getCreatedAt())
            .sentAt(notification.getSentAt())
            .failedAt(notification.getFailedAt())
            .retryCount(notification.getRetryCount())
            .errorMessage(notification.getErrorMessage())
            .build();
    }

    /**
     * Convert entity to domain model
     */
    private Notification toDomain(NotificationEntity entity) {
        return Notification.builder()
            .id(entity.getId())
            .type(entity.getType())
            .channel(entity.getChannel())
            .status(entity.getStatus())
            .recipient(entity.getRecipient())
            .subject(entity.getSubject())
            .body(entity.getBody())
            .metadata(entity.getMetadata())
            .templateName(entity.getTemplateName())
            .templateVariables(entity.getTemplateVariables())
            .invoiceId(entity.getInvoiceId())
            .invoiceNumber(entity.getInvoiceNumber())
            .correlationId(entity.getCorrelationId())
            .createdAt(entity.getCreatedAt())
            .sentAt(entity.getSentAt())
            .failedAt(entity.getFailedAt())
            .retryCount(entity.getRetryCount())
            .errorMessage(entity.getErrorMessage())
            .build();
    }
}
