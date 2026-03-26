package com.wpanther.notification.application.usecase;

import com.wpanther.notification.application.port.in.event.saga.SagaCompletedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaFailedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaStartedEvent;
import com.wpanther.notification.application.port.in.event.saga.SagaStepCompletedEvent;

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
