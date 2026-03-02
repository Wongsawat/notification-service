package com.wpanther.notification.application.service;

import com.wpanther.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for dispatching notifications asynchronously.
 *
 * <p>This separate service fixes the @Async self-invocation problem where calling an async
 * method from within the same bean bypasses the Spring proxy. By extracting to a separate
 * service, the @Async proxy is properly invoked.</p>
 *
 * <p>Uses REQUIRES_NEW propagation to ensure each async dispatch gets its own transaction,
 * independent of the caller's transaction context.</p>
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
     * Dispatch notification asynchronously with a new transaction.
     *
     * <p>Runs in a separate thread with its own transaction context. If sending fails,
     * only this transaction rolls back, not the caller's.</p>
     *
     * @param notification the notification to send
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchAsync(Notification notification) {
        log.debug("Dispatching notification asynchronously: id={}, thread={}",
            notification.getId(), Thread.currentThread().getName());

        sendingService.sendNotification(notification);

        log.debug("Async dispatch completed: id={}", notification.getId());
    }
}
