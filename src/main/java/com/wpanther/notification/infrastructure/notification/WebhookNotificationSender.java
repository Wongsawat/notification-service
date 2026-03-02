package com.wpanther.notification.infrastructure.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Webhook notification sender implementation using WebClient
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookNotificationSender implements NotificationSender {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);

    @Override
    public void send(Notification notification) throws NotificationException {
        try {
            log.info("Sending webhook notification: id={}, url={}",
                notification.getId(), notification.getRecipient());

            // Build webhook payload
            Map<String, Object> payload = buildPayload(notification);

            // Send HTTP POST to webhook URL with retry on transient failures
            String response = webClient.post()
                .uri(notification.getRecipient())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(
                    HttpStatusCode::isError,
                    clientResponse -> {
                        log.error("Webhook returned error status: {}", clientResponse.statusCode());
                        return Mono.error(new RuntimeException("Webhook returned error: " + clientResponse.statusCode()));
                    }
                )
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRIES, INITIAL_BACKOFF)
                    .filter(throwable -> isTransient(throwable))
                    .doBeforeRetry(signal -> log.warn("Retrying webhook send: attempt={}, error={}",
                        signal.totalRetries() + 1, signal.failure().getMessage())))
                .block();

            log.info("Webhook sent successfully: id={}, response={}", notification.getId(), response);

        } catch (Exception e) {
            log.error("Failed to send webhook notification: id={}, url={}",
                notification.getId(), notification.getRecipient(), e);
            throw new NotificationException("Failed to send webhook", e);
        }
    }

    /**
     * Determine if an exception is transient (should retry)
     */
    private boolean isTransient(Throwable throwable) {
        return throwable instanceof TimeoutException
            || throwable instanceof WebClientRequestException
            || (throwable instanceof RuntimeException
                && throwable.getMessage() != null
                && throwable.getMessage().contains("5xx"));  // 5xx errors from onStatus
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.WEBHOOK;
    }

    /**
     * Build webhook payload from notification
     */
    private Map<String, Object> buildPayload(Notification notification) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("notificationId", notification.getId().toString());
        payload.put("type", notification.getType().name());
        payload.put("subject", notification.getSubject());
        payload.put("body", notification.getBody());
        payload.put("invoiceId", notification.getInvoiceId());
        payload.put("invoiceNumber", notification.getInvoiceNumber());
        payload.put("correlationId", notification.getCorrelationId());
        payload.put("timestamp", notification.getCreatedAt().toString());

        // Include metadata
        if (notification.getMetadata() != null) {
            payload.put("metadata", notification.getMetadata());
        }

        // Include template variables if applicable
        if (notification.usesTemplate() && notification.getTemplateVariables() != null) {
            payload.put("data", notification.getTemplateVariables());
        }

        return payload;
    }
}
