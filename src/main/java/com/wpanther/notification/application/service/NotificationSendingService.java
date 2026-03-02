package com.wpanther.notification.application.service;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.domain.repository.NotificationRepository;
import com.wpanther.notification.domain.service.NotificationSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Core notification sending service.
 *
 * <p>Owns the full send lifecycle: persist → markSending → dispatch → markSent/markFailed.
 * Extracted from NotificationService to break the circular dependency with
 * NotificationDispatcherService.</p>
 *
 * <p>Dependency chain (no cycles):
 * NotificationService → NotificationDispatcherService → NotificationSendingService</p>
 */
@Service
@Slf4j
public class NotificationSendingService {

    private final NotificationRepository repository;
    private final List<NotificationSender> senders;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

    public NotificationSendingService(NotificationRepository repository,
                                      List<NotificationSender> senders) {
        this.repository = repository;
        this.senders = senders;
    }

    /**
     * Persists, sends, and updates notification status in a single transaction.
     * Never throws — failures are captured as FAILED status so the transaction can commit.
     */
    @Transactional
    public Notification sendNotification(Notification notification) {
        try {
            log.info("Sending notification: id={}, type={}, channel={}",
                notification.getId(), notification.getType(), notification.getChannel());

            notification = repository.save(notification);

            notification.markSending();
            notification = repository.save(notification);

            findSender(notification.getChannel()).send(notification);

            notification.markSent();
            notification = repository.save(notification);

            log.info("Notification sent successfully: id={}", notification.getId());
            return notification;

        } catch (Exception e) {
            log.error("Failed to send notification: id={}", notification.getId(), e);
            // Do NOT re-throw — @Transactional must commit the FAILED status.
            notification.markFailed(e.getMessage());
            notification = repository.save(notification);
            return notification;
        }
    }

    /**
     * Creates a notification from a template and immediately sends it.
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
     * Returns notification counts grouped by status.
     */
    public Map<String, Long> getStatistics() {
        return Map.of(
            "pending",  repository.countByStatus(NotificationStatus.PENDING),
            "sending",  repository.countByStatus(NotificationStatus.SENDING),
            "sent",     repository.countByStatus(NotificationStatus.SENT),
            "failed",   repository.countByStatus(NotificationStatus.FAILED),
            "retrying", repository.countByStatus(NotificationStatus.RETRYING)
        );
    }

    /**
     * Finds a sender that supports the given channel.
     *
     * @throws IllegalStateException if no registered sender supports the channel
     */
    private NotificationSender findSender(NotificationChannel channel) {
        return senders.stream()
            .filter(sender -> sender.supports(channel))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No sender found for channel: " + channel));
    }
}
