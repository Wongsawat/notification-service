package com.wpanther.notification.domain.model;

/**
 * Notification delivery channels
 */
public enum NotificationChannel {
    EMAIL,      // Email notification
    SMS,        // SMS notification (future)
    WEBHOOK,    // HTTP webhook callback
    IN_APP      // In-app notification (future)
}
