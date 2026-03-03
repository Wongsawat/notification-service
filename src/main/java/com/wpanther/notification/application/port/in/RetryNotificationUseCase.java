package com.wpanther.notification.application.port.in;

import java.util.UUID;

/**
 * Input port: use case for retrying a failed notification.
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 */
public interface RetryNotificationUseCase {

    /**
     * Prepare a FAILED notification for retry and dispatch it asynchronously.
     *
     * @throws java.util.NoSuchElementException  if no notification found for id
     * @throws IllegalStateException             if notification cannot be retried
     */
    void prepareAndDispatchRetry(UUID id);
}
