package com.wpanther.notification.application.port.in;

import com.wpanther.notification.infrastructure.messaging.saga.SagaCompletedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaFailedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaStartedEvent;
import com.wpanther.notification.infrastructure.messaging.saga.SagaStepCompletedEvent;

/**
 * Input port: use case for handling saga orchestration lifecycle events.
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 *
 * <p>SagaStarted and SagaStepCompleted are logged only.
 * SagaCompleted sends a completion email; SagaFailed sends an urgent failure email.</p>
 */
public interface SagaEventUseCase {

    void handleSagaStarted(SagaStartedEvent event);

    void handleSagaStepCompleted(SagaStepCompletedEvent event);

    void handleSagaCompleted(SagaCompletedEvent event);

    void handleSagaFailed(SagaFailedEvent event);
}
