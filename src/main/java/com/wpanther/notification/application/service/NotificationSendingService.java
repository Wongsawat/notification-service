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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * Core notification sending service.
 *
 * <p>Owns the full send lifecycle: persist → markSending → [I/O] → markSent/markFailed.
 * Each DB phase runs in its own short REQUIRES_NEW transaction, releasing the connection
 * before blocking on external I/O (SMTP or HTTP). This prevents connection pool exhaustion
 * under load.</p>
 *
 * <p>Dependency chain (no cycles):
 * NotificationService → NotificationDispatcherService → NotificationSendingService</p>
 */
@Service
@Slf4j
public class NotificationSendingService {

    private final NotificationRepository repository;
    private final List<NotificationSender> senders;
    private final TransactionTemplate requiresNewTx;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

    public NotificationSendingService(NotificationRepository repository,
                                      List<NotificationSender> senders,
                                      PlatformTransactionManager txManager) {
        this.repository = repository;
        this.senders = senders;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Sends a notification using a three-phase approach to avoid holding a DB connection
     * open during external I/O:
     * <ol>
     *   <li>Short tx: persist + markSending</li>
     *   <li>No tx: actual sender I/O (email SMTP or webhook HTTP)</li>
     *   <li>Short tx: markSent or markFailed</li>
     * </ol>
     * Never throws — failures are captured as FAILED status.
     */
    public Notification sendNotification(Notification notification) {
        log.info("Sending notification: id={}, type={}, channel={}",
            notification.getId(), notification.getType(), notification.getChannel());

        // Phase 1: persist + mark SENDING (short transaction, releases connection before I/O)
        Notification sending = requiresNewTx.execute(status -> {
            Notification saved = repository.save(notification);
            saved.markSending();
            return repository.save(saved);
        });

        try {
            // Phase 2: external I/O — no transaction, no DB connection held
            findSender(sending.getChannel()).send(sending);

            // Phase 3a (success): mark SENT (short transaction)
            sending.markSent();
            Notification result = requiresNewTx.execute(status -> repository.save(sending));
            log.info("Notification sent successfully: id={}", sending.getId());
            return result;

        } catch (Exception e) {
            log.error("Failed to send notification: id={}, type={}, channel={}",
                sending.getId(), sending.getType(), sending.getChannel(), e);
            // Phase 3b (failure): mark FAILED (short transaction)
            sending.markFailed(e.getMessage());
            return requiresNewTx.execute(status -> repository.save(sending));
        }
    }

    /**
     * Creates a notification from a template and immediately sends it.
     */
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
