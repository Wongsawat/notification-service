package com.wpanther.notification.domain.model;

/**
 * Notification delivery status
 */
public enum NotificationStatus {
    PENDING,        // Waiting to be sent
    SENDING,        // Currently being sent
    SENT,           // Successfully sent
    FAILED,         // Failed to send
    RETRYING        // Retrying after failure
}
