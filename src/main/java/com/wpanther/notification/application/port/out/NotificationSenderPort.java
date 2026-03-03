package com.wpanther.notification.application.port.out;

import com.wpanther.notification.domain.exception.NotificationException;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;

/**
 * Output port: contract for sending a notification via a specific channel.
 * Implemented by EmailNotificationSenderAdapter and WebhookNotificationSenderAdapter.
 */
public interface NotificationSenderPort {

    /**
     * Send notification through the appropriate channel.
     * @throws NotificationException if sending fails
     */
    void send(Notification notification) throws NotificationException;

    /**
     * Returns true if this sender handles the given channel.
     */
    boolean supports(NotificationChannel channel);
}
