package com.wpanther.notification.infrastructure.adapter.in.scheduler;

import com.wpanther.notification.application.usecase.QueryNotificationUseCase;
import com.wpanther.notification.application.usecase.RetryNotificationUseCase;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Inbound scheduler adapter: drives scheduled notification maintenance tasks.
 * <p>
 * Injects use-case interfaces only — never the concrete NotificationService.
 * Three tasks:
 *   1. Recover stale SENDING notifications (every 2 min)
 *   2. Retry failed notifications (every 5 min)
 *   3. Process pending notifications (every 1 min)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSchedulerAdapter {

    private final RetryNotificationUseCase retryUseCase;
    private final QueryNotificationUseCase queryUseCase;
    private final NotificationRepository repository;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

    @Value("${app.notification.stale-sending-timeout-ms:300000}")
    private long staleSendingTimeoutMs;

    /**
     * Recovers notifications stuck in SENDING state (e.g. after a process crash).
     * Resets them to FAILED so the normal retry sweeper can pick them up.
     */
    @Scheduled(fixedDelayString = "${app.notification.stale-sending-check-interval:120000}")
    @Transactional
    public void recoverStaleSendingNotifications() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(staleSendingTimeoutMs / 1000);
        List<Notification> stale = repository.findStaleSendingNotifications(threshold, 100);

        if (stale.isEmpty()) {
            return;
        }

        log.warn("Found {} stale SENDING notifications to recover", stale.size());

        for (Notification notification : stale) {
            try {
                notification.markFailed("Recovered from stale SENDING state");
                repository.save(notification);
                log.info("Recovered stale notification: id={}", notification.getId());
            } catch (Exception e) {
                log.error("Failed to recover stale notification: id={}", notification.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.notification.retry-interval:300000}")
    public void retryFailedNotifications() {
        List<Notification> failedNotifications = repository.findFailedNotifications(maxRetries, 100);
        log.info("Found {} failed notifications to retry", failedNotifications.size());
        failedNotifications.forEach(n -> {
            try {
                retryUseCase.prepareAndDispatchRetry(n.getId());
            } catch (Exception e) {
                log.error("Failed to retry notification: id={}", n.getId(), e);
            }
        });
    }

    @Scheduled(fixedDelayString = "${app.notification.processing-interval:60000}")
    public void processPendingNotifications() {
        List<Notification> pending = repository.findPendingNotifications(100);
        log.debug("Processing {} pending notifications", pending.size());
        pending.forEach(n -> {
            try {
                retryUseCase.prepareAndDispatchRetry(n.getId());
            } catch (Exception e) {
                log.error("Failed to process pending notification: id={}", n.getId(), e);
            }
        });
    }
}
