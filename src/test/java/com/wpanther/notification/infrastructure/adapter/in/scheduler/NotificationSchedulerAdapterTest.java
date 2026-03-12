package com.wpanther.notification.infrastructure.adapter.in.scheduler;

import com.wpanther.notification.application.usecase.QueryNotificationUseCase;
import com.wpanther.notification.application.usecase.RetryNotificationUseCase;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerAdapterTest {

    @Mock private RetryNotificationUseCase retryUseCase;
    @Mock private QueryNotificationUseCase queryUseCase;
    @Mock private NotificationRepository repository;

    @InjectMocks private NotificationSchedulerAdapter scheduler;

    @Test
    void retryFailedNotifications_delegatesToRetryUseCaseForEachFailed() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Notification n1 = mock(Notification.class);
        Notification n2 = mock(Notification.class);
        when(n1.getId()).thenReturn(id1);
        when(n2.getId()).thenReturn(id2);
        when(repository.findFailedNotifications(anyInt(), anyInt())).thenReturn(List.of(n1, n2));

        scheduler.retryFailedNotifications();

        verify(retryUseCase).prepareAndDispatchRetry(id1);
        verify(retryUseCase).prepareAndDispatchRetry(id2);
    }

    @Test
    void retryFailedNotifications_continuesOnException() {
        Notification n = mock(Notification.class);
        when(n.getId()).thenReturn(UUID.randomUUID());
        when(repository.findFailedNotifications(anyInt(), anyInt())).thenReturn(List.of(n));
        doThrow(new RuntimeException("send failed")).when(retryUseCase).prepareAndDispatchRetry(any());

        // Should not throw
        scheduler.retryFailedNotifications();
    }

    @Test
    void processPendingNotifications_delegatesToRetryUseCaseForEachPending() {
        UUID id = UUID.randomUUID();
        Notification n = mock(Notification.class);
        when(n.getId()).thenReturn(id);
        when(repository.findPendingNotifications(anyInt())).thenReturn(List.of(n));

        scheduler.processPendingNotifications();

        verify(retryUseCase).prepareAndDispatchRetry(id);
    }

    @Test
    void recoverStaleSendingNotifications_marksStaleFailed() {
        Notification n = mock(Notification.class);
        when(repository.findStaleSendingNotifications(any(LocalDateTime.class), anyInt()))
            .thenReturn(List.of(n));

        scheduler.recoverStaleSendingNotifications();

        verify(n).markFailed("Recovered from stale SENDING state");
        verify(repository).save(n);
    }

    @Test
    void recoverStaleSendingNotifications_noopWhenNoneStale() {
        when(repository.findStaleSendingNotifications(any(), anyInt())).thenReturn(List.of());

        scheduler.recoverStaleSendingNotifications();

        verifyNoInteractions(retryUseCase);
    }

    @Test
    void allThreeMethodsHaveScheduledAnnotation() {
        String[] scheduledMethods = {"retryFailedNotifications", "processPendingNotifications",
                                      "recoverStaleSendingNotifications"};
        for (String methodName : scheduledMethods) {
            Method method = Arrays.stream(NotificationSchedulerAdapter.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Method not found: " + methodName));
            assertThat(method.isAnnotationPresent(Scheduled.class))
                .as("Method %s should be @Scheduled", methodName)
                .isTrue();
        }
    }

    @Test
    void injectsRetryUseCaseInterface_notConcreteClass() throws Exception {
        var field = NotificationSchedulerAdapter.class.getDeclaredField("retryUseCase");
        assertThat(field.getType()).isEqualTo(RetryNotificationUseCase.class);
    }

    @Test
    void injectsQueryUseCaseInterface_notConcreteClass() throws Exception {
        var field = NotificationSchedulerAdapter.class.getDeclaredField("queryUseCase");
        assertThat(field.getType()).isEqualTo(QueryNotificationUseCase.class);
    }
}
