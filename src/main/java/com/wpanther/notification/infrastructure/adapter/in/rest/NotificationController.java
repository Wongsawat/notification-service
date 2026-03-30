package com.wpanther.notification.infrastructure.adapter.in.rest;

import com.wpanther.notification.application.usecase.QueryNotificationUseCase;
import com.wpanther.notification.application.usecase.RetryNotificationUseCase;
import com.wpanther.notification.application.usecase.SendNotificationUseCase;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * REST API for notification operations
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final SendNotificationUseCase sendNotificationUseCase;
    private final QueryNotificationUseCase queryNotificationUseCase;
    private final RetryNotificationUseCase retryNotificationUseCase;

    /**
     * Send notification manually
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> sendNotification(
        @Valid @RequestBody NotificationRequest request
    ) {
        log.info("Received notification request: type={}, channel={}, recipient={}",
            request.type(), request.channel(), request.recipient());

        Notification notification;

        if (request.templateName() != null) {
            notification = Notification.createFromTemplate(
                request.type(),
                request.channel(),
                request.recipient(),
                request.templateName(),
                request.templateVariables()
            );
            notification.setSubject(request.subject());
        } else {
            notification = Notification.create(
                request.type(),
                request.channel(),
                request.recipient(),
                request.subject(),
                request.body()
            );
        }

        notification.setDocumentId(request.documentId());
        notification.setDocumentNumber(request.documentNumber());
        notification.setCorrelationId(request.correlationId());

        if (request.metadata() != null) {
            request.metadata().forEach(notification::addMetadata);
        }

        notification = sendNotificationUseCase.sendNotification(notification);

        return ResponseEntity.ok(Map.of(
            "notificationId", notification.getId(),
            "status", notification.getStatus(),
            "createdAt", notification.getCreatedAt(),
            "sentAt", notification.getSentAt()
        ));
    }

    /**
     * Get notification by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Notification> getNotification(@PathVariable UUID id) {
        return queryNotificationUseCase.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get notifications by document ID
     */
    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<Notification>> getNotificationsByDocumentId(
        @PathVariable String documentId,
        @RequestParam(defaultValue = "200") int limit
    ) {
        return ResponseEntity.ok(queryNotificationUseCase.findByDocumentId(documentId, limit));
    }

    /**
     * Get notifications by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Notification>> getNotificationsByStatus(
        @PathVariable NotificationStatus status,
        @RequestParam(defaultValue = "200") int limit
    ) {
        return ResponseEntity.ok(queryNotificationUseCase.findByStatus(status, limit));
    }

    /**
     * Get notification statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Long>> getStatistics() {
        return ResponseEntity.ok(queryNotificationUseCase.getStatistics());
    }

    /**
     * Retry failed notification
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, String>> retryNotification(@PathVariable UUID id) {
        try {
            retryNotificationUseCase.prepareAndDispatchRetry(id);
            return ResponseEntity.ok(Map.of("message", "Retry scheduled"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Request DTO
     */
    public record NotificationRequest(
        @NotNull(message = "Notification type is required") NotificationType type,
        @NotNull(message = "Notification channel is required") NotificationChannel channel,
        @NotBlank(message = "Recipient is required") String recipient,
        String subject,
        String body,
        String templateName,
        Map<String, Object> templateVariables,
        Map<String, Object> metadata,
        String documentId,
        String documentNumber,
        String correlationId
    ) {}
}
