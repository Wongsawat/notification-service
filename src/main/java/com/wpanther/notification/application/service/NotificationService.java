package com.wpanther.notification.application.service;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for notification operations.
 *
 * <p>Thin facade that delegates sending to {@link NotificationSendingService} and dispatching
 * to {@link NotificationDispatcherService}. Owns the scheduled retry/pending sweepers, and
 * exposes query and retry-initiation methods for the REST controller.</p>
 *
 * <p>Dependency chain (no cycles):
 * NotificationService → NotificationDispatcherService → NotificationSendingService</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationSendingService sendingService;
    private final NotificationDispatcherService dispatcherService;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

    // ── Send (delegates to NotificationSendingService) ──────────────────────────────────

    public Notification sendNotification(Notification notification) {
        return sendingService.sendNotification(notification);
    }

    public Notification createAndSend(NotificationType type, NotificationChannel channel,
                                      String recipient, String templateName,
                                      Map<String, Object> templateVariables) {
        return sendingService.createAndSend(type, channel, recipient, templateName, templateVariables);
    }

    public Map<String, Long> getStatistics() {
        return sendingService.getStatistics();
    }

    // ── Query methods (for REST controller) ─────────────────────────────────────────────

    public Optional<Notification> findById(UUID id) {
        return repository.findById(id);
    }

    public List<Notification> findByInvoiceId(String invoiceId) {
        return repository.findByInvoiceId(invoiceId);
    }

    public List<Notification> findByStatus(NotificationStatus status) {
        return repository.findByStatus(status);
    }

    // ── Manual retry (for REST controller) ──────────────────────────────────────────────

    /**
     * Prepares a failed notification for retry and schedules async dispatch.
     *
     * @throws NoSuchElementException if no notification exists for the given ID
     * @throws IllegalStateException  if the notification cannot be retried
     */
    @Transactional
    public void prepareAndDispatchRetry(UUID id) {
        Notification notification = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Notification not found: " + id));

        if (!notification.canRetry(maxRetries)) {
            throw new IllegalStateException("Cannot retry notification");
        }

        notification.prepareRetry();
        repository.save(notification);
        dispatcherService.dispatchAsync(notification);
    }

    // ── Scheduled sweepers ───────────────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${app.notification.retry-interval:300000}")
    @Transactional
    public void retryFailedNotifications() {
        List<Notification> failedNotifications = repository.findFailedNotifications(maxRetries, 100);

        log.info("Found {} failed notifications to retry", failedNotifications.size());

        for (Notification notification : failedNotifications) {
            try {
                log.info("Retrying notification: id={}, attempt={}",
                    notification.getId(), notification.getRetryCount() + 1);

                notification.prepareRetry();
                notification = repository.save(notification);

                dispatcherService.dispatchAsync(notification);

            } catch (Exception e) {
                log.error("Failed to retry notification: id={}", notification.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.notification.processing-interval:60000}")
    @Transactional
    public void processPendingNotifications() {
        List<Notification> pendingNotifications = repository.findPendingNotifications(100);

        log.debug("Processing {} pending notifications", pendingNotifications.size());

        for (Notification notification : pendingNotifications) {
            try {
                dispatcherService.dispatchAsync(notification);
            } catch (Exception e) {
                log.error("Failed to process pending notification: id={}", notification.getId(), e);
            }
        }
    }
}
