package com.wpanther.notification.application.usecase;

import com.wpanther.notification.application.port.in.event.DocumentReceivedEvent;

/**
 * Input port: use case for handling document received events (statistics).
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 *
 * <p>These events are logged only; no email notifications are created.</p>
 */
public interface DocumentReceivedEventUseCase {

    void handleDocumentReceived(DocumentReceivedEvent event);
}
