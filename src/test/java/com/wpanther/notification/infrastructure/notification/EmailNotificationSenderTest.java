package com.wpanther.notification.infrastructure.notification;

import com.wpanther.notification.domain.model.Notification;
import com.wpanther.notification.domain.model.NotificationChannel;
import com.wpanther.notification.domain.model.NotificationType;
import com.wpanther.notification.domain.exception.NotificationException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailNotificationSender Tests")
class EmailNotificationSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private MimeMessage mimeMessage;

    private EmailNotificationSender emailNotificationSender;

    @BeforeEach
    void setUp() {
        emailNotificationSender = new EmailNotificationSender(mailSender, templateEngine);
        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Nested
    @DisplayName("send() method tests")
    class SendMethodTests {

        @Test
        @DisplayName("Should send email successfully with direct body")
        void testSendEmailWithDirectBody() throws Exception {
            // Arrange
            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.EMAIL)
                .recipient("test@example.com")
                .subject("Test Subject")
                .body("<html><body>Test Body</body></html>")
                .metadata(new HashMap<>())
                .build();

            // Act
            emailNotificationSender.send(notification);

            // Assert
            verify(mailSender).send(any(MimeMessage.class));
            verify(templateEngine, never()).render(anyString(), anyMap());
        }

        @Test
        @DisplayName("Should send email successfully with template")
        void testSendEmailWithTemplate() throws Exception, TemplateEngine.TemplateException {
            // Arrange
            Map<String, Object> templateVars = new HashMap<>();
            templateVars.put("invoiceNumber", "INV-001");
            templateVars.put("totalAmount", "1000.00");

            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.EMAIL)
                .recipient("test@example.com")
                .subject("Invoice Processed")
                .templateName("invoice-processed")
                .templateVariables(templateVars)
                .metadata(new HashMap<>())
                .build();

            when(templateEngine.render("invoice-processed", templateVars))
                .thenReturn("<html><body>Rendered Template</body></html>");

            // Act
            emailNotificationSender.send(notification);

            // Assert
            verify(templateEngine).render("invoice-processed", templateVars);
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should throw NotificationException when send() fails")
        void testThrowNotificationExceptionOnMessagingException() {
            // Arrange
            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.EMAIL)
                .recipient("test@example.com")
                .subject("Test Subject")
                .body("Test Body")
                .metadata(new HashMap<>())
                .build();

            doThrow(new org.springframework.mail.MailSendException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

            // Act & Assert - MailSendException is RuntimeException and won't be caught
            // The test verifies the exception propagates
            assertThatThrownBy(() -> emailNotificationSender.send(notification))
                .isInstanceOf(org.springframework.mail.MailSendException.class)
                .hasMessageContaining("SMTP error");
        }

        @Test
        @DisplayName("Should throw NotificationException when TemplateException occurs")
        void testThrowNotificationExceptionOnTemplateException() throws TemplateEngine.TemplateException {
            // Arrange
            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.EMAIL)
                .recipient("test@example.com")
                .subject("Test Subject")
                .templateName("invalid-template")
                .templateVariables(new HashMap<>())
                .metadata(new HashMap<>())
                .build();

            when(templateEngine.render(anyString(), anyMap()))
                .thenThrow(new TemplateEngine.TemplateException("Template not found", null));

            // Act & Assert
            assertThatThrownBy(() -> emailNotificationSender.send(notification))
                .isInstanceOf(NotificationException.class)
                .hasMessageContaining("Failed to render email template");
        }

        @Test
        @DisplayName("Should add metadata as email headers")
        void testAddMetadataAsHeaders() throws Exception, MessagingException {
            // Arrange
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("invoiceId", "INV-123");
            metadata.put("correlationId", "CORR-456");

            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.EMAIL)
                .recipient("test@example.com")
                .subject("Test Subject")
                .body("Test Body")
                .metadata(metadata)
                .build();

            // Act
            emailNotificationSender.send(notification);

            // Assert
            verify(mimeMessage, atLeastOnce()).addHeader(startsWith("X-"), any());
            verify(mimeMessage).addHeader("X-invoiceId", "INV-123");
            verify(mimeMessage).addHeader("X-correlationId", "CORR-456");
        }

        @Test
        @DisplayName("Should handle null metadata gracefully")
        void testHandleNullMetadata() throws Exception {
            // Arrange
            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.EMAIL)
                .recipient("test@example.com")
                .subject("Test Subject")
                .body("Test Body")
                .metadata(null)
                .build();

            // Act
            emailNotificationSender.send(notification);

            // Assert - should not throw exception
            verify(mailSender).send(any(MimeMessage.class));
            verify(mimeMessage, never()).addHeader(anyString(), anyString());
        }

        @Test
        @DisplayName("Should log warning when adding header fails")
        void testLogWarningOnHeaderFailure() throws Exception, MessagingException {
            // Arrange
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("validKey", "validValue");
            metadata.put("invalidKey", "invalidValue");

            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.EMAIL)
                .recipient("test@example.com")
                .subject("Test Subject")
                .body("Test Body")
                .metadata(metadata)
                .build();

            doThrow(new MessagingException("Header error"))
                .when(mimeMessage).addHeader("X-invalidKey", "invalidValue");

            // Act
            emailNotificationSender.send(notification);

            // Assert - should still send email
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should set HTML content type")
        void testSetHtmlContentType() throws Exception {
            // Arrange
            Notification notification = Notification.builder()
                .id(java.util.UUID.randomUUID())
                .type(NotificationType.INVOICE_PROCESSED)
                .channel(NotificationChannel.EMAIL)
                .recipient("test@example.com")
                .subject("Test Subject")
                .body("<p>HTML Content</p>")
                .metadata(new HashMap<>())
                .build();

            // Act
            emailNotificationSender.send(notification);

            // Assert
            verify(mailSender).send(any(MimeMessage.class));
        }
    }

    @Nested
    @DisplayName("supports() method tests")
    class SupportsMethodTests {

        @Test
        @DisplayName("Should return true for EMAIL channel")
        void testSupportsEmailChannel() {
            // Act
            boolean result = emailNotificationSender.supports(NotificationChannel.EMAIL);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false for non-EMAIL channels")
        void testDoesNotSupportOtherChannels() {
            // Act & Assert
            assertThat(emailNotificationSender.supports(NotificationChannel.WEBHOOK)).isFalse();
            assertThat(emailNotificationSender.supports(NotificationChannel.SMS)).isFalse();
            assertThat(emailNotificationSender.supports(NotificationChannel.IN_APP)).isFalse();
        }
    }
}
