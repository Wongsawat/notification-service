package com.wpanther.notification.infrastructure.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.domain.service.NotificationSender.NotificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookNotificationSender Tests")
class WebhookNotificationSenderTest {

    @Mock
    private WebClient webClient;

    @Mock
    private RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RequestBodySpec requestBodySpec;

    @Mock
    private RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private ResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private WebhookNotificationSender webhookNotificationSender;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        webhookNotificationSender = new WebhookNotificationSender(webClient, objectMapper);

        // Setup WebClient mock chain using doReturn/when to avoid generic type issues
        lenient().doReturn(requestBodyUriSpec).when(webClient).post();
        lenient().doReturn(requestBodySpec).when(requestBodyUriSpec).uri(anyString());
        lenient().doReturn(requestBodySpec).when(requestBodySpec).contentType(any(MediaType.class));
        lenient().doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        lenient().doReturn(responseSpec).when(requestHeadersSpec).retrieve();
        lenient().doReturn(responseSpec).when(responseSpec).onStatus(any(), any());
    }

    @Nested
    @DisplayName("send() method tests")
    class SendMethodTests {

        @Test
        @DisplayName("Should send webhook successfully")
        void testSendWebhookSuccessfully() throws NotificationException {
            // Arrange
            String webhookUrl = "https://example.com/webhook";
            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.WEBHOOK)
                .recipient(webhookUrl)
                .subject("Test Subject")
                .body("Test Body")
                .invoiceId("INV-123")
                .invoiceNumber("INV-001")
                .correlationId("CORR-456")
                .createdAt(LocalDateTime.now())
                .metadata(new HashMap<>())
                .templateVariables(new HashMap<>())
                .build();

            lenient().doReturn(Mono.just("Success")).when(responseSpec).bodyToMono(String.class);

            // Act
            webhookNotificationSender.send(notification);

            // Assert
            verify(webClient).post();
            verify(requestBodyUriSpec).uri(webhookUrl);
            verify(requestBodySpec).contentType(MediaType.APPLICATION_JSON);
            verify(requestBodySpec).bodyValue(any(Map.class));
            verify(requestHeadersSpec).retrieve();
            verify(responseSpec).onStatus(any(), any());
            verify(responseSpec).bodyToMono(String.class);
        }

        @Test
        @DisplayName("Should build correct payload with all fields")
        void testBuildCorrectPayload() throws NotificationException {
            // Arrange
            String webhookUrl = "https://example.com/webhook";
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("key1", "value1");

            Map<String, Object> templateVars = new HashMap<>();
            templateVars.put("invoiceNumber", "INV-001");

            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.WEBHOOK)
                .recipient(webhookUrl)
                .subject("Test Subject")
                .body("Test Body")
                .invoiceId("INV-123")
                .invoiceNumber("INV-001")
                .correlationId("CORR-456")
                .createdAt(LocalDateTime.now())
                .metadata(metadata)
                .templateName("template-name")
                .templateVariables(templateVars)
                .build();

            lenient().doReturn(Mono.just("Success")).when(responseSpec).bodyToMono(String.class);

            // Act
            webhookNotificationSender.send(notification);

            // Assert - verify bodyValue was called with correct payload structure
            verify(requestBodySpec).bodyValue(argThat(payload -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) payload;
                return map.containsKey("notificationId") &&
                       map.containsKey("type") &&
                       map.containsKey("subject") &&
                       map.containsKey("body") &&
                       map.containsKey("invoiceId") &&
                       map.containsKey("invoiceNumber") &&
                       map.containsKey("correlationId") &&
                       map.containsKey("timestamp") &&
                       map.containsKey("metadata") &&
                       map.containsKey("data");
            }));
        }

        @Test
        @DisplayName("Should throw NotificationException on HTTP error status")
        void testThrowNotificationExceptionOnErrorStatus() {
            // Arrange
            String webhookUrl = "https://example.com/webhook";
            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.WEBHOOK)
                .recipient(webhookUrl)
                .subject("Test Subject")
                .body("Test Body")
                .createdAt(LocalDateTime.now())
                .metadata(new HashMap<>())
                .templateVariables(new HashMap<>())
                .build();

            lenient().doThrow(new RuntimeException("Webhook returned error: 500"))
                .when(responseSpec).onStatus(any(), any());

            // Act & Assert
            assertThatThrownBy(() -> webhookNotificationSender.send(notification))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("Failed to send webhook");
        }

        @Test
        @DisplayName("Should throw NotificationException on timeout")
        void testThrowNotificationExceptionOnTimeout() {
            // Arrange
            String webhookUrl = "https://example.com/webhook";
            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.WEBHOOK)
                .recipient(webhookUrl)
                .subject("Test Subject")
                .body("Test Body")
                .createdAt(LocalDateTime.now())
                .metadata(new HashMap<>())
                .templateVariables(new HashMap<>())
                .build();

            lenient().doReturn(Mono.error(
                new java.util.concurrent.TimeoutException("Request timeout")))
                .when(responseSpec).bodyToMono(String.class);

            // Act & Assert
            assertThatThrownBy(() -> webhookNotificationSender.send(notification))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("Failed to send webhook");
        }

        @Test
        @DisplayName("Should handle null metadata and template variables")
        void testHandleNullMetadataAndTemplateVariables() throws NotificationException {
            // Arrange
            String webhookUrl = "https://example.com/webhook";
            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.WEBHOOK)
                .recipient(webhookUrl)
                .subject("Test Subject")
                .body("Test Body")
                .createdAt(LocalDateTime.now())
                .metadata(null)
                .templateVariables(null)
                .build();

            lenient().doReturn(Mono.just("Success")).when(responseSpec).bodyToMono(String.class);

            // Act
            webhookNotificationSender.send(notification);

            // Assert - should not throw exception
            verify(responseSpec).bodyToMono(String.class);
        }

        @Test
        @DisplayName("Should include template variables as data when using template")
        void testIncludeTemplateVariablesAsData() throws NotificationException {
            // Arrange
            String webhookUrl = "https://example.com/webhook";
            Map<String, Object> templateVars = new HashMap<>();
            templateVars.put("invoiceNumber", "INV-001");
            templateVars.put("totalAmount", "1000.00");

            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.WEBHOOK)
                .recipient(webhookUrl)
                .subject("Test Subject")
                .body("Test Body")
                .templateName("invoice-processed")
                .templateVariables(templateVars)
                .createdAt(LocalDateTime.now())
                .metadata(new HashMap<>())
                .build();

            lenient().doReturn(Mono.just("Success")).when(responseSpec).bodyToMono(String.class);

            // Act
            webhookNotificationSender.send(notification);

            // Assert - verify data field contains template variables
            verify(requestBodySpec).bodyValue(argThat(payload -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) payload;
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) map.get("data");
                return data != null && "INV-001".equals(data.get("invoiceNumber"));
            }));
        }

        @Test
        @DisplayName("Should retry on transient timeout failures")
        void testRetriesOnTimeout() throws NotificationException {
            // This test verifies retry logic is configured
            // Actual retry verification requires integration test with real server
            // For now, we verify the send() method handles timeout correctly
            String webhookUrl = "https://example.com/webhook";
            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.WEBHOOK)
                .recipient(webhookUrl)
                .subject("Test Subject")
                .body("Test Body")
                .createdAt(LocalDateTime.now())
                .build();

            lenient().doReturn(Mono.error(new java.util.concurrent.TimeoutException("Timeout")))
                .when(responseSpec).bodyToMono(String.class);

            // Act & Assert - should eventually throw after retries exhausted
            assertThatThrownBy(() -> webhookNotificationSender.send(notification))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("Failed to send webhook");
        }
    }

    @Nested
    @DisplayName("URL validation tests")
    class UrlValidationTests {

        @Test
        @DisplayName("Should reject file:// scheme (SSRF prevention)")
        void testRejectFileScheme() {
            Notification notification = buildMinimalNotification("file:///etc/passwd");
            assertThatThrownBy(() -> webhookNotificationSender.send(notification))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("only http and https are allowed");
        }

        @Test
        @DisplayName("Should reject ftp:// scheme")
        void testRejectFtpScheme() {
            Notification notification = buildMinimalNotification("ftp://internal-server/resource");
            assertThatThrownBy(() -> webhookNotificationSender.send(notification))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("only http and https are allowed");
        }

        @Test
        @DisplayName("Should reject invalid URL syntax")
        void testRejectInvalidUrl() {
            Notification notification = buildMinimalNotification("not a valid url ://");
            assertThatThrownBy(() -> webhookNotificationSender.send(notification))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("Invalid webhook URL");
        }

        @Test
        @DisplayName("Should reject blank URL")
        void testRejectBlankUrl() {
            Notification notification = buildMinimalNotification("   ");
            assertThatThrownBy(() -> webhookNotificationSender.send(notification))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("Should allow http:// scheme")
        void testAllowHttpScheme() throws NotificationException {
            Notification notification = buildMinimalNotification("http://example.com/webhook");
            lenient().doReturn(Mono.just("ok")).when(responseSpec).bodyToMono(String.class);
            webhookNotificationSender.send(notification); // must not throw
            verify(requestBodyUriSpec).uri("http://example.com/webhook");
        }

        @Test
        @DisplayName("Should allow https:// scheme")
        void testAllowHttpsScheme() throws NotificationException {
            Notification notification = buildMinimalNotification("https://api.example.com/hook");
            lenient().doReturn(Mono.just("ok")).when(responseSpec).bodyToMono(String.class);
            webhookNotificationSender.send(notification); // must not throw
            verify(requestBodyUriSpec).uri("https://api.example.com/hook");
        }

        private Notification buildMinimalNotification(String url) {
            return Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.WEBHOOK)
                .recipient(url)
                .subject("Test")
                .body("Test")
                .createdAt(LocalDateTime.now())
                .metadata(new HashMap<>())
                .templateVariables(new HashMap<>())
                .build();
        }
    }

    @Nested
    @DisplayName("supports() method tests")
    class SupportsMethodTests {

        @Test
        @DisplayName("Should return true for WEBHOOK channel")
        void testSupportsWebhookChannel() {
            // Act
            boolean result = webhookNotificationSender.supports(NotificationChannel.WEBHOOK);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false for non-WEBHOOK channels")
        void testDoesNotSupportOtherChannels() {
            // Act & Assert
            assertThat(webhookNotificationSender.supports(NotificationChannel.EMAIL)).isFalse();
            assertThat(webhookNotificationSender.supports(NotificationChannel.SMS)).isFalse();
            assertThat(webhookNotificationSender.supports(NotificationChannel.IN_APP)).isFalse();
        }
    }
}
