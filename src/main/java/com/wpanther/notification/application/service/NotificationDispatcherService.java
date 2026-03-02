package com.wpanther.notification.application.service;

import com.wpanther.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for dispatching notifications asynchronously.
 *
 * <p>This separate service fixes the @Async self-invocation problem where calling an async
 * method from within the same bean bypasses the Spring proxy. By extracting to a separate
 * service, the @Async proxy is properly invoked.</p>
 *
 * <p>Transaction management is handled by {@link NotificationSendingService} via short
 * REQUIRES_NEW transactions, so no @Transactional annotation is needed here.</p>
 *
 * <p>Depends on {@link NotificationSendingService} (not {@link NotificationService}) to avoid
 * a circular dependency: NotificationService → NotificationDispatcherService →
 * NotificationSendingService.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcherService {

    private final NotificationSendingService sendingService;

    /**
     * Dispatch notification asynchronously. Transaction management is owned by
     * {@link NotificationSendingService#sendNotification}, which uses short REQUIRES_NEW
     * transactions around DB operations, releasing the connection before external I/O.
     *
     * @param notification the notification to send
     */
    @Async
    public void dispatchAsync(Notification notification) {
        log.debug("Dispatching notification asynchronously: id={}, thread={}",
            notification.getId(), Thread.currentThread().getName());

        sendingService.sendNotification(notification);

        log.debug("Async dispatch completed: id={}", notification.getId());
    }
}
