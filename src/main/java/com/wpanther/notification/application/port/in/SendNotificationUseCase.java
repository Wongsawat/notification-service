package com.wpanther.notification.application.port.in;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationType;

import java.util.Map;

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
}
