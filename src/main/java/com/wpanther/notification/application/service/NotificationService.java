package com.wpanther.notification.application.service;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.domain.repository.NotificationRepository;
import com.wpanther.notification.domain.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Application service for notification operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final List<NotificationSender> senders;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

    /**
     * Send notification
     */
    @Transactional
    public Notification sendNotification(Notification notification) {
        try {
            log.info("Sending notification: id={}, type={}, channel={}",
                notification.getId(), notification.getType(), notification.getChannel());

            // Save as pending
            notification = repository.save(notification);

            // Mark as sending
            notification.markSending();
            notification = repository.save(notification);

            // Find appropriate sender and send notification
            NotificationSender sender = findSender(notification.getChannel());
            sender.send(notification);

            // Mark as sent
            notification.markSent();
            notification = repository.save(notification);

            log.info("Notification sent successfully: id={}", notification.getId());
            return notification;

        } catch (Exception e) {
            log.error("Failed to send notification: id={}", notification.getId(), e);

            // Mark as failed and persist — do NOT re-throw, so @Transactional commits the
            // FAILED status. Re-throwing would roll back the transaction and lose this state.
            notification.markFailed(e.getMessage());
            notification = repository.save(notification);
            return notification;
        }
    }

    /**
     * Send notification asynchronously
     */
    @Async
    @Transactional
    public void sendNotificationAsync(Notification notification) {
        sendNotification(notification);
    }

    /**
     * Create and send notification from template
     */
    @Transactional
    public Notification createAndSend(NotificationType type, NotificationChannel channel,
                                     String recipient, String templateName,
                                     Map<String, Object> templateVariables) {
        Notification notification = Notification.createFromTemplate(
            type, channel, recipient, templateName, templateVariables
        );

        return sendNotification(notification);
    }

    /**
     * Retry failed notifications (scheduled task)
     */
    @Scheduled(fixedDelayString = "${app.notification.retry-interval:300000}") // 5 minutes
    @Transactional
    public void retryFailedNotifications() {
        List<Notification> failedNotifications = repository.findFailedNotifications(maxRetries);

        log.info("Found {} failed notifications to retry", failedNotifications.size());

        for (Notification notification : failedNotifications) {
            try {
                log.info("Retrying notification: id={}, attempt={}",
                    notification.getId(), notification.getRetryCount() + 1);

                notification.prepareRetry();
                notification = repository.save(notification);

                sendNotificationAsync(notification);

            } catch (Exception e) {
                log.error("Failed to retry notification: id={}", notification.getId(), e);
            }
        }
    }

    /**
     * Process pending notifications (scheduled task)
     */
    @Scheduled(fixedDelayString = "${app.notification.processing-interval:60000}") // 1 minute
    @Transactional
    public void processPendingNotifications() {
        List<Notification> pendingNotifications = repository.findPendingNotifications();

        log.debug("Processing {} pending notifications", pendingNotifications.size());

        for (Notification notification : pendingNotifications) {
            try {
                sendNotificationAsync(notification);
            } catch (Exception e) {
                log.error("Failed to process pending notification: id={}", notification.getId(), e);
            }
        }
    }

    /**
     * Get notification statistics
     */
    public Map<String, Long> getStatistics() {
        return Map.of(
            "pending", repository.countByStatus(NotificationStatus.PENDING),
            "sending", repository.countByStatus(NotificationStatus.SENDING),
            "sent", repository.countByStatus(NotificationStatus.SENT),
            "failed", repository.countByStatus(NotificationStatus.FAILED),
            "retrying", repository.countByStatus(NotificationStatus.RETRYING)
        );
    }

    /**
     * Find sender for channel
     * @throws IllegalStateException if no sender supports the channel
     */
    private NotificationSender findSender(NotificationChannel channel) {
        return senders.stream()
            .filter(sender -> sender.supports(channel))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No sender found for channel: " + channel));
    }
}
