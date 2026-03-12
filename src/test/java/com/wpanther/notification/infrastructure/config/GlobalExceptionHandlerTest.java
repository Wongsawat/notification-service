package com.wpanther.notification.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.notification.application.usecase.QueryNotificationUseCase;
import com.wpanther.notification.application.usecase.RetryNotificationUseCase;
import com.wpanther.notification.application.usecase.SendNotificationUseCase;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.infrastructure.adapter.in.rest.NotificationController;
import com.wpanther.notification.infrastructure.config.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {NotificationController.class, GlobalExceptionHandler.class})
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

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

    @Test
    @DisplayName("Validation errors return structured response with field errors")
    void testValidationErrors_returnStructuredResponse() throws Exception {
        Map<String, Object> request = Map.of(
            "channel", "EMAIL",
            "recipient", "test@example.com",
            "subject", "Test",
            "body", "Test body"
            // Missing 'type' which has @NotNull
        );

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.errors").exists())
            .andExpect(jsonPath("$.errors.type").exists());
    }

    @Test
    @DisplayName("Multiple validation errors return all field errors")
    void testMultipleValidationErrors_returnAllFieldErrors() throws Exception {
        Map<String, Object> request = Map.of(
            // Missing type, channel, recipient - all required
            "subject", "Test",
            "body", "Test body"
        );

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.type").exists())
            .andExpect(jsonPath("$.errors.channel").exists())
            .andExpect(jsonPath("$.errors.recipient").exists());
    }

    @Test
    @DisplayName("Blank recipient returns validation error")
    void testBlankRecipient_returnsValidationError() throws Exception {
        Map<String, Object> request = Map.of(
            "type", "INVOICE_PROCESSED",
            "channel", "EMAIL",
            "recipient", "   ",  // blank
            "subject", "Test",
            "body", "Test body"
        );

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.recipient").exists());
    }

    @Test
    @DisplayName("Service exception returns 400 with message")
    void testServiceException_returns400WithMessage() throws Exception {
        Map<String, Object> request = Map.of(
            "type", "INVOICE_PROCESSED",
            "channel", "EMAIL",
            "recipient", "test@example.com",
            "subject", "Test",
            "body", "Test body"
        );

        when(sendNotificationUseCase.sendNotification(any()))
            .thenThrow(new IllegalStateException("Can only start sending from PENDING status"));

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Can only start sending from PENDING status"))
            .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @DisplayName("Generic exception returns 500 with safe message")
    void testGenericException_returns500WithSafeMessage() throws Exception {
        Map<String, Object> request = Map.of(
            "type", "INVOICE_PROCESSED",
            "channel", "EMAIL",
            "recipient", "test@example.com",
            "subject", "Test",
            "body", "Test body"
        );

        when(sendNotificationUseCase.sendNotification(any()))
            .thenThrow(new RuntimeException("Unexpected database error"));

        mockMvc.perform(post("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.message").value("Internal server error"))
            .andExpect(jsonPath("$.errors").isEmpty());
    }
}
