package com.wpanther.notification.domain.service;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;

/**
 * Domain service interface for sending notifications
 */
public interface NotificationSender {

    /**
     * Send notification through appropriate channel
     */
    void send(Notification notification) throws NotificationException;

    /**
     * Check if sender supports the channel
     */
    boolean supports(NotificationChannel channel);

    /**
     * Exception for notification sending failures
     */
    class NotificationException extends Exception {
        public NotificationException(String message) {
            super(message);
        }

        public NotificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
