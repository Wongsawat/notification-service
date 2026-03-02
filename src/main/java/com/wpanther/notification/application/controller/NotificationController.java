package com.wpanther.notification.application.controller;

import com.wpanther.notification.application.service.NotificationDispatcherService;
import com.wpanther.notification.application.service.NotificationService;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.domain.repository.NotificationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for notification operations
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final NotificationDispatcherService dispatcherService;

    @Value("${app.notification.max-retries:3}")
    private int maxRetries;

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

        notification.setInvoiceId(request.invoiceId());
        notification.setInvoiceNumber(request.invoiceNumber());
        notification.setCorrelationId(request.correlationId());

        if (request.metadata() != null) {
            request.metadata().forEach(notification::addMetadata);
        }

        notification = notificationService.sendNotification(notification);

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
        return notificationRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get notifications by invoice ID
     */
    @GetMapping("/invoice/{invoiceId}")
    public ResponseEntity<List<Notification>> getNotificationsByInvoice(
        @PathVariable String invoiceId
    ) {
        List<Notification> notifications = notificationRepository.findByInvoiceId(invoiceId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get notifications by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Notification>> getNotificationsByStatus(
        @PathVariable NotificationStatus status
    ) {
        List<Notification> notifications = notificationRepository.findByStatus(status);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get notification statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Long>> getStatistics() {
        return ResponseEntity.ok(notificationService.getStatistics());
    }

    /**
     * Retry failed notification
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, String>> retryNotification(@PathVariable UUID id) {
        return notificationRepository.findById(id)
            .map(notification -> {
                if (notification.canRetry(maxRetries)) {
                    notification.prepareRetry();
                    notificationRepository.save(notification);
                    dispatcherService.dispatchAsync(notification);
                    return ResponseEntity.ok(Map.of("message", "Retry scheduled"));
                } else {
                    return ResponseEntity.badRequest()
                        .body(Map.of("error", "Cannot retry notification"));
                }
            })
            .orElse(ResponseEntity.notFound().build());
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
        String invoiceId,
        String invoiceNumber,
        String correlationId
    ) {}
}
