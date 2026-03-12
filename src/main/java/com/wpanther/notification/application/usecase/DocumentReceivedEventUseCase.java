package com.wpanther.notification.application.usecase;

import com.wpanther.notification.application.dto.event.DocumentReceivedCountingEvent;
import com.wpanther.notification.application.dto.event.DocumentReceivedEvent;

/**
 * Input port: use case for handling document received events (statistics and counting).
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 *
 * <p>These events are logged only; no email notifications are created.</p>
 */
public interface DocumentReceivedEventUseCase {

    void handleDocumentCounting(DocumentReceivedCountingEvent event);

    void handleDocumentReceived(DocumentReceivedEvent event);
}
