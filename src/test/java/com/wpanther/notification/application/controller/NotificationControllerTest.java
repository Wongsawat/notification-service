package com.wpanther.notification.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.notification.application.service.NotificationService;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationStatus;
import com.wpanther.notification.domain.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@DisplayName("NotificationController REST API Tests")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private Notification testNotification;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testNotification = Notification.builder()
            .id(testId)
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test Subject")
            .status(NotificationStatus.SENT)
            .createdAt(LocalDateTime.now())
            .sentAt(LocalDateTime.now())
            .build();
    }

    // ── POST /api/v1/notifications Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/notifications should send notification with plain body")
    void testSendNotificationWithPlainBody() throws Exception {
        Map<String, Object> request = Map.of(
            "type", "INVOICE_PROCESSED",
            "channel", "EMAIL",
            "recipient", "test@example.com",
            "subject", "Invoice Processed",
            "body", "Your invoice has been processed",
            "invoiceId", "invoice-uuid",
            "invoiceNumber", "INV-001"
        );

        when(notificationService.sendNotification(any())).thenReturn(testNotification);

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationId").value(testId.toString()))
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.sentAt").exists());

        verify(notificationService).sendNotification(any(Notification.class));
    }

    @Test
    @DisplayName("POST /api/v1/notifications should send notification with template")
    void testSendNotificationWithTemplate() throws Exception {
        Map<String, Object> request = Map.of(
            "type", "INVOICE_PROCESSED",
            "channel", "EMAIL",
            "recipient", "test@example.com",
            "subject", "Invoice Processed",
            "templateName", "invoice-processed",
            "templateVariables", Map.of("invoiceNumber", "INV-001", "amount", 1500.00),
            "invoiceId", "invoice-uuid",
            "invoiceNumber", "INV-001",
            "correlationId", "correlation-uuid"
        );

        when(notificationService.sendNotification(any())).thenReturn(testNotification);

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationId").exists())
            .andExpect(jsonPath("$.status").value("SENT"));

        verify(notificationService).sendNotification(any(Notification.class));
    }

    @Test
    @DisplayName("POST /api/v1/notifications should include metadata when provided")
    void testSendNotificationWithMetadata() throws Exception {
        Map<String, Object> request = Map.of(
            "type", "INVOICE_PROCESSED",
            "channel", "EMAIL",
            "recipient", "test@example.com",
            "subject", "Invoice Processed",
            "body", "Test body",
            "metadata", Map.of("priority", "HIGH", "source", "API")
        );

        when(notificationService.sendNotification(any())).thenReturn(testNotification);

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationId").exists());

        verify(notificationService).sendNotification(any(Notification.class));
    }

    // ── GET /api/v1/notifications/{id} Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/notifications/{id} should return notification when found")
    void testGetNotificationByIdFound() throws Exception {
        when(notificationService.findById(testId)).thenReturn(Optional.of(testNotification));

        mockMvc.perform(get("/api/v1/notifications/" + testId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testId.toString()))
            .andExpect(jsonPath("$.recipient").value("test@example.com"))
            .andExpect(jsonPath("$.status").value("SENT"));

        verify(notificationService).findById(testId);
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{id} should return 404 when not found")
    void testGetNotificationByIdNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(notificationService.findById(unknownId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/notifications/" + unknownId))
            .andExpect(status().isNotFound());

        verify(notificationService).findById(unknownId);
    }

    // ── GET /api/v1/notifications/invoice/{invoiceId} Tests ──────────────────────────────

    @Test
    @DisplayName("GET /api/v1/notifications/invoice/{invoiceId} should return notifications for invoice")
    void testGetNotificationsByInvoiceId() throws Exception {
        String invoiceId = "invoice-uuid";
        when(notificationService.findByInvoiceId(invoiceId)).thenReturn(List.of(testNotification));

        mockMvc.perform(get("/api/v1/notifications/invoice/" + invoiceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").value(testId.toString()));

        verify(notificationService).findByInvoiceId(invoiceId);
    }

    @Test
    @DisplayName("GET /api/v1/notifications/invoice/{invoiceId} should return empty list when none found")
    void testGetNotificationsByInvoiceIdEmpty() throws Exception {
        String invoiceId = "unknown-invoice";
        when(notificationService.findByInvoiceId(invoiceId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notifications/invoice/" + invoiceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        verify(notificationService).findByInvoiceId(invoiceId);
    }

    // ── GET /api/v1/notifications/status/{status} Tests ──────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/notifications/status/{status} should return notifications by status")
    void testGetNotificationsByStatus() throws Exception {
        when(notificationService.findByStatus(NotificationStatus.SENT)).thenReturn(List.of(testNotification));

        mockMvc.perform(get("/api/v1/notifications/status/SENT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].status").value("SENT"));

        verify(notificationService).findByStatus(NotificationStatus.SENT);
    }

    // ── GET /api/v1/notifications/statistics Tests ────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/notifications/statistics should return notification counts by status")
    void testGetStatistics() throws Exception {
        Map<String, Long> stats = Map.of(
            "pending", 5L,
            "sending", 2L,
            "sent", 100L,
            "failed", 3L,
            "retrying", 1L
        );
        when(notificationService.getStatistics()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/notifications/statistics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pending").value(5))
            .andExpect(jsonPath("$.sending").value(2))
            .andExpect(jsonPath("$.sent").value(100))
            .andExpect(jsonPath("$.failed").value(3))
            .andExpect(jsonPath("$.retrying").value(1));

        verify(notificationService).getStatistics();
    }

    // ── POST /api/v1/notifications/{id}/retry Tests ───────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/retry should retry failed notification when allowed")
    void testRetryNotificationSuccess() throws Exception {
        // prepareAndDispatchRetry is void — does nothing by default on a mock
        doNothing().when(notificationService).prepareAndDispatchRetry(testId);

        mockMvc.perform(post("/api/v1/notifications/" + testId + "/retry"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Retry scheduled"));

        verify(notificationService).prepareAndDispatchRetry(testId);
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/retry should return 400 when retry not allowed")
    void testRetryNotificationWhenNotAllowed() throws Exception {
        doThrow(new IllegalStateException("Cannot retry notification"))
            .when(notificationService).prepareAndDispatchRetry(testId);

        mockMvc.perform(post("/api/v1/notifications/" + testId + "/retry"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Cannot retry notification"));

        verify(notificationService).prepareAndDispatchRetry(testId);
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/retry should return 404 when notification not found")
    void testRetryNotificationNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        doThrow(new NoSuchElementException("Notification not found: " + unknownId))
            .when(notificationService).prepareAndDispatchRetry(unknownId);

        mockMvc.perform(post("/api/v1/notifications/" + unknownId + "/retry"))
            .andExpect(status().isNotFound());

        verify(notificationService).prepareAndDispatchRetry(unknownId);
    }

    // ── Input Validation Tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/notifications should return 400 when type is missing")
    void testSendNotification_missingType_returns400() throws Exception {
        Map<String, Object> request = Map.of(
            "channel", "EMAIL",
            "recipient", "test@example.com",
            "subject", "Test",
            "body", "Test body"
        );

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    @DisplayName("POST /api/v1/notifications should return 400 when channel is missing")
    void testSendNotification_missingChannel_returns400() throws Exception {
        Map<String, Object> request = Map.of(
            "type", "INVOICE_PROCESSED",
            "recipient", "test@example.com",
            "subject", "Test",
            "body", "Test body"
        );

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    @DisplayName("POST /api/v1/notifications should return 400 when recipient is missing")
    void testSendNotification_missingRecipient_returns400() throws Exception {
        Map<String, Object> request = Map.of(
            "type", "INVOICE_PROCESSED",
            "channel", "EMAIL",
            "subject", "Test",
            "body", "Test body"
        );

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    @DisplayName("POST /api/v1/notifications should return 400 when recipient is blank")
    void testSendNotification_blankRecipient_returns400() throws Exception {
        Map<String, Object> request = Map.of(
            "type", "INVOICE_PROCESSED",
            "channel", "EMAIL",
            "recipient", "   ",
            "subject", "Test",
            "body", "Test body"
        );

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());

        verify(notificationService, never()).sendNotification(any());
    }
}
