package com.wpanther.notification.application.usecase;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationType;

import java.util.Map;
import java.util.UUID;

/**
 * Input port: use case for sending notifications.
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 */
public interface SendNotificationUseCase {

    /**
     * Persist and send a fully-constructed notification synchronously.
     */
    Notification sendNotification(Notification notification);

    /**
     * Create a template-based notification and send it synchronously.
     */
    Notification createAndSend(NotificationType type, NotificationChannel channel,
                                String recipient, String templateName,
                                Map<String, Object> templateVariables);

    /**
     * Dispatch a PENDING notification asynchronously.
     *
     * <p>Intended for the scheduler sweeper that recovers notifications stuck in PENDING
     * state (e.g. after a restart). Unlike {@code prepareAndDispatchRetry}, this method
     * does not perform a FAILED→RETRYING state transition — it simply hands the PENDING
     * notification to the async dispatcher, which will execute PENDING→SENDING internally.</p>
     *
     * @throws java.util.NoSuchElementException if no notification found for id
     * @throws IllegalStateException            if the notification is not in PENDING status
     */
    void dispatchPending(UUID id);
}
