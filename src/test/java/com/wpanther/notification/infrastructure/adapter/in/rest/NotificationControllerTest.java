package com.wpanther.notification.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.notification.application.usecase.QueryNotificationUseCase;
import com.wpanther.notification.application.usecase.RetryNotificationUseCase;
import com.wpanther.notification.application.usecase.SendNotificationUseCase;
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
    private SendNotificationUseCase sendNotificationUseCase;

    @MockBean
    private QueryNotificationUseCase queryNotificationUseCase;

    @MockBean
    private RetryNotificationUseCase retryNotificationUseCase;

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

        when(sendNotificationUseCase.sendNotification(any())).thenReturn(testNotification);

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationId").value(testId.toString()))
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.sentAt").exists());

        verify(sendNotificationUseCase).sendNotification(any(Notification.class));
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

        when(sendNotificationUseCase.sendNotification(any())).thenReturn(testNotification);

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationId").exists())
            .andExpect(jsonPath("$.status").value("SENT"));

        verify(sendNotificationUseCase).sendNotification(any(Notification.class));
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

        when(sendNotificationUseCase.sendNotification(any())).thenReturn(testNotification);

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationId").exists());

        verify(sendNotificationUseCase).sendNotification(any(Notification.class));
    }

    // ── GET /api/v1/notifications/{id} Tests ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/notifications/{id} should return notification when found")
    void testGetNotificationByIdFound() throws Exception {
        when(queryNotificationUseCase.findById(testId)).thenReturn(Optional.of(testNotification));

        mockMvc.perform(get("/api/v1/notifications/" + testId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testId.toString()))
            .andExpect(jsonPath("$.recipient").value("test@example.com"))
            .andExpect(jsonPath("$.status").value("SENT"));

        verify(queryNotificationUseCase).findById(testId);
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{id} should return 404 when not found")
    void testGetNotificationByIdNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(queryNotificationUseCase.findById(unknownId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/notifications/" + unknownId))
            .andExpect(status().isNotFound());

        verify(queryNotificationUseCase).findById(unknownId);
    }

    // ── GET /api/v1/notifications/invoice/{invoiceId} Tests ──────────────────────────────

    @Test
    @DisplayName("GET /api/v1/notifications/invoice/{invoiceId} should return notifications for invoice")
    void testGetNotificationsByInvoiceId() throws Exception {
        String invoiceId = "invoice-uuid";
        when(queryNotificationUseCase.findByInvoiceId(invoiceId, 200)).thenReturn(List.of(testNotification));

        mockMvc.perform(get("/api/v1/notifications/invoice/" + invoiceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].id").value(testId.toString()));

        verify(queryNotificationUseCase).findByInvoiceId(invoiceId, 200);
    }

    @Test
    @DisplayName("GET /api/v1/notifications/invoice/{invoiceId} should honour explicit limit param")
    void testGetNotificationsByInvoiceIdWithLimit() throws Exception {
        String invoiceId = "invoice-uuid";
        when(queryNotificationUseCase.findByInvoiceId(invoiceId, 50)).thenReturn(List.of(testNotification));

        mockMvc.perform(get("/api/v1/notifications/invoice/" + invoiceId).param("limit", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(testId.toString()));

        verify(queryNotificationUseCase).findByInvoiceId(invoiceId, 50);
    }

    @Test
    @DisplayName("GET /api/v1/notifications/invoice/{invoiceId} should return empty list when none found")
    void testGetNotificationsByInvoiceIdEmpty() throws Exception {
        String invoiceId = "unknown-invoice";
        when(queryNotificationUseCase.findByInvoiceId(invoiceId, 200)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/notifications/invoice/" + invoiceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        verify(queryNotificationUseCase).findByInvoiceId(invoiceId, 200);
    }

    // ── GET /api/v1/notifications/status/{status} Tests ──────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/notifications/status/{status} should return notifications by status")
    void testGetNotificationsByStatus() throws Exception {
        when(queryNotificationUseCase.findByStatus(NotificationStatus.SENT, 200)).thenReturn(List.of(testNotification));

        mockMvc.perform(get("/api/v1/notifications/status/SENT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].status").value("SENT"));

        verify(queryNotificationUseCase).findByStatus(NotificationStatus.SENT, 200);
    }

    @Test
    @DisplayName("GET /api/v1/notifications/status/{status} should honour explicit limit param")
    void testGetNotificationsByStatusWithLimit() throws Exception {
        when(queryNotificationUseCase.findByStatus(NotificationStatus.FAILED, 10)).thenReturn(List.of(testNotification));

        mockMvc.perform(get("/api/v1/notifications/status/FAILED").param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("SENT"));

        verify(queryNotificationUseCase).findByStatus(NotificationStatus.FAILED, 10);
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
        when(queryNotificationUseCase.getStatistics()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/notifications/statistics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pending").value(5))
            .andExpect(jsonPath("$.sending").value(2))
            .andExpect(jsonPath("$.sent").value(100))
            .andExpect(jsonPath("$.failed").value(3))
            .andExpect(jsonPath("$.retrying").value(1));

        verify(queryNotificationUseCase).getStatistics();
    }

    // ── POST /api/v1/notifications/{id}/retry Tests ───────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/retry should retry failed notification when allowed")
    void testRetryNotificationSuccess() throws Exception {
        // prepareAndDispatchRetry is void — does nothing by default on a mock
        doNothing().when(retryNotificationUseCase).prepareAndDispatchRetry(testId);

        mockMvc.perform(post("/api/v1/notifications/" + testId + "/retry"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Retry scheduled"));

        verify(retryNotificationUseCase).prepareAndDispatchRetry(testId);
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/retry should return 400 when retry not allowed")
    void testRetryNotificationWhenNotAllowed() throws Exception {
        doThrow(new IllegalStateException("Cannot retry notification"))
            .when(retryNotificationUseCase).prepareAndDispatchRetry(testId);

        mockMvc.perform(post("/api/v1/notifications/" + testId + "/retry"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Cannot retry notification"));

        verify(retryNotificationUseCase).prepareAndDispatchRetry(testId);
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/retry should return 404 when notification not found")
    void testRetryNotificationNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        doThrow(new NoSuchElementException("Notification not found: " + unknownId))
            .when(retryNotificationUseCase).prepareAndDispatchRetry(unknownId);

        mockMvc.perform(post("/api/v1/notifications/" + unknownId + "/retry"))
            .andExpect(status().isNotFound());

        verify(retryNotificationUseCase).prepareAndDispatchRetry(unknownId);
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

        verify(sendNotificationUseCase, never()).sendNotification(any());
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

        verify(sendNotificationUseCase, never()).sendNotification(any());
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

        verify(sendNotificationUseCase, never()).sendNotification(any());
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

        verify(sendNotificationUseCase, never()).sendNotification(any());
    }
}
