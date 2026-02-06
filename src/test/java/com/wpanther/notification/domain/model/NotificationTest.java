package com.wpanther.notification.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Notification Aggregate Root Tests")
class NotificationTest {

    private Notification.NotificationBuilder builder;

    @BeforeEach
    void setUp() {
        builder = Notification.builder()
            .id(UUID.randomUUID())
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .recipient("test@example.com")
            .subject("Test Subject")
            .status(NotificationStatus.PENDING)
            .metadata(new HashMap<>())
            .templateVariables(new HashMap<>())
            .createdAt(LocalDateTime.now())
            .retryCount(0);
    }

    // ========== Builder Pattern Tests ==========

    @Test
    @DisplayName("Should build notification with all fields")
    void testBuilderWithAllFields() {
        // Arrange
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> metadata = Map.of("key1", "value1");
        Map<String, Object> templateVars = Map.of("var1", "val1");

        // Act
        Notification notification = Notification.builder()
            .id(id)
            .type(NotificationType.INVOICE_PROCESSED)
            .channel(NotificationChannel.EMAIL)
            .status(NotificationStatus.PENDING)
            .recipient("test@example.com")
            .subject("Test Subject")
            .body("Test Body")
            .metadata(metadata)
            .templateName("test-template")
            .templateVariables(templateVars)
            .invoiceId("invoice-uuid")
            .invoiceNumber("INV-001")
            .correlationId("correlation-uuid")
            .createdAt(now)
            .retryCount(0)
            .build();

        // Assert
        assertThat(notification.getId()).isEqualTo(id);
        assertThat(notification.getType()).isEqualTo(NotificationType.INVOICE_PROCESSED);
        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRecipient()).isEqualTo("test@example.com");
        assertThat(notification.getSubject()).isEqualTo("Test Subject");
        assertThat(notification.getBody()).isEqualTo("Test Body");
        assertThat(notification.getMetadata()).isEqualTo(metadata);
        assertThat(notification.getTemplateName()).isEqualTo("test-template");
        assertThat(notification.getTemplateVariables()).isEqualTo(templateVars);
        assertThat(notification.getInvoiceId()).isEqualTo("invoice-uuid");
        assertThat(notification.getInvoiceNumber()).isEqualTo("INV-001");
        assertThat(notification.getCorrelationId()).isEqualTo("correlation-uuid");
        assertThat(notification.getCreatedAt()).isEqualTo(now);
        assertThat(notification.getRetryCount()).isZero();
    }

    // ========== Factory Method Tests ==========

    @Test
    @DisplayName("Should create notification with factory method")
    void testCreateFactoryMethod() {
        // Act
        Notification notification = Notification.create(
            NotificationType.INVOICE_PROCESSED,
            NotificationChannel.EMAIL,
            "test@example.com",
            "Test Subject",
            "Test Body"
        );

        // Assert
        assertThat(notification.getId()).isNotNull();
        assertThat(notification.getType()).isEqualTo(NotificationType.INVOICE_PROCESSED);
        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRecipient()).isEqualTo("test@example.com");
        assertThat(notification.getSubject()).isEqualTo("Test Subject");
        assertThat(notification.getBody()).isEqualTo("Test Body");
        assertThat(notification.getMetadata()).isNotNull().isEmpty();
        assertThat(notification.getTemplateVariables()).isNotNull().isEmpty();
        assertThat(notification.getCreatedAt()).isNotNull();
        assertThat(notification.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("Should create notification from template with factory method")
    void testCreateFromTemplateFactoryMethod() {
        // Arrange
        Map<String, Object> templateVars = Map.of(
            "invoiceNumber", "INV-001",
            "totalAmount", 1500.00,
            "currency", "THB"
        );

        // Act
        Notification notification = Notification.createFromTemplate(
            NotificationType.INVOICE_PROCESSED,
            NotificationChannel.EMAIL,
            "test@example.com",
            "invoice-processed",
            templateVars
        );

        // Assert
        assertThat(notification.getId()).isNotNull();
        assertThat(notification.getType()).isEqualTo(NotificationType.INVOICE_PROCESSED);
        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRecipient()).isEqualTo("test@example.com");
        assertThat(notification.getTemplateName()).isEqualTo("invoice-processed");
        assertThat(notification.getTemplateVariables()).containsAllEntriesOf(templateVars);
        assertThat(notification.getMetadata()).isNotNull().isEmpty();
        assertThat(notification.getCreatedAt()).isNotNull();
        assertThat(notification.getRetryCount()).isZero();
    }

    // ========== State Transition Tests - Valid Transitions ==========

    @Test
    @DisplayName("Should transition from PENDING to SENDING")
    void testMarkSendingFromPending() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.PENDING).build();

        // Act
        notification.markSending();

        // Assert
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENDING);
    }

    @Test
    @DisplayName("Should transition from RETRYING to SENDING")
    void testMarkSendingFromRetrying() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.RETRYING).build();

        // Act
        notification.markSending();

        // Assert
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENDING);
    }

    @Test
    @DisplayName("Should transition from SENDING to SENT and set sentAt timestamp")
    void testMarkSent() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.SENDING).build();
        assertThat(notification.getSentAt()).isNull();

        // Act
        notification.markSent();

        // Assert
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isNotNull();
        assertThat(notification.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Should transition from SENDING to FAILED and set failedAt timestamp")
    void testMarkFailed() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.SENDING).build();
        String errorMessage = "SMTP connection failed";
        assertThat(notification.getFailedAt()).isNull();

        // Act
        notification.markFailed(errorMessage);

        // Assert
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailedAt()).isNotNull();
        assertThat(notification.getErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("Should transition from FAILED to RETRYING and increment retryCount")
    void testPrepareRetry() {
        // Arrange
        Notification notification = builder
            .status(NotificationStatus.FAILED)
            .retryCount(1)
            .build();

        // Act
        notification.prepareRetry();

        // Assert
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRYING);
        assertThat(notification.getRetryCount()).isEqualTo(2);
    }

    // ========== State Transition Tests - Invalid Transitions ==========

    @Test
    @DisplayName("Should throw IllegalStateException when marking SENT as SENDING")
    void testMarkSendingThrowsFromSentStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.SENT).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.markSending())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING or RETRYING");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when marking FAILED as SENDING")
    void testMarkSendingThrowsFromFailedStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.FAILED).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.markSending())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING or RETRYING");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when marking SENDING as SENDING")
    void testMarkSendingThrowsFromSendingStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.SENDING).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.markSending())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING or RETRYING");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when marking PENDING as SENT")
    void testMarkSentThrowsFromPendingStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.PENDING).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.markSent())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SENDING");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when marking RETRYING as SENT")
    void testMarkSentThrowsFromRetryingStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.RETRYING).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.markSent())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SENDING");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when marking FAILED as SENT")
    void testMarkSentThrowsFromFailedStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.FAILED).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.markSent())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SENDING");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when marking SENT as SENT")
    void testMarkSentThrowsFromSentStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.SENT).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.markSent())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SENDING");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when marking PENDING as FAILED")
    void testMarkFailedThrowsFromPendingStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.PENDING).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.markFailed("error"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SENDING");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when marking RETRYING as FAILED")
    void testMarkFailedThrowsFromRetryingStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.RETRYING).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.markFailed("error"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SENDING");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when marking SENT as FAILED")
    void testMarkFailedThrowsFromSentStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.SENT).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.markFailed("error"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SENDING");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when marking FAILED as FAILED")
    void testMarkFailedThrowsFromFailedStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.FAILED).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.markFailed("error"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SENDING");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when preparing retry from PENDING")
    void testPrepareRetryThrowsFromPendingStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.PENDING).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.prepareRetry())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FAILED");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when preparing retry from SENDING")
    void testPrepareRetryThrowsFromSendingStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.SENDING).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.prepareRetry())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FAILED");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when preparing retry from SENT")
    void testPrepareRetryThrowsFromSentStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.SENT).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.prepareRetry())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FAILED");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when preparing retry from RETRYING")
    void testPrepareRetryThrowsFromRetryingStatus() {
        // Arrange
        Notification notification = builder.status(NotificationStatus.RETRYING).build();

        // Act & Assert
        assertThatThrownBy(() -> notification.prepareRetry())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("FAILED");
    }

    // ========== Retry Logic Tests ==========

    @Test
    @DisplayName("Should allow retry when status is FAILED and retryCount < maxRetries")
    void testCanRetryWhenBelowMaxRetries() {
        // Arrange
        Notification notification = builder
            .status(NotificationStatus.FAILED)
            .retryCount(2)
            .build();
        int maxRetries = 3;

        // Act
        boolean canRetry = notification.canRetry(maxRetries);

        // Assert
        assertThat(canRetry).isTrue();
    }

    @Test
    @DisplayName("Should not allow retry when retryCount equals maxRetries")
    void testCannotRetryWhenEqualMaxRetries() {
        // Arrange
        Notification notification = builder
            .status(NotificationStatus.FAILED)
            .retryCount(3)
            .build();
        int maxRetries = 3;

        // Act
        boolean canRetry = notification.canRetry(maxRetries);

        // Assert
        assertThat(canRetry).isFalse();
    }

    @Test
    @DisplayName("Should not allow retry when retryCount exceeds maxRetries")
    void testCannotRetryWhenExceedsMaxRetries() {
        // Arrange
        Notification notification = builder
            .status(NotificationStatus.FAILED)
            .retryCount(4)
            .build();
        int maxRetries = 3;

        // Act
        boolean canRetry = notification.canRetry(maxRetries);

        // Assert
        assertThat(canRetry).isFalse();
    }

    @Test
    @DisplayName("Should not allow retry when status is not FAILED")
    void testCannotRetryWhenNotFailedStatus() {
        // Arrange
        Notification notification = builder
            .status(NotificationStatus.SENDING)
            .retryCount(0)
            .build();
        int maxRetries = 3;

        // Act
        boolean canRetry = notification.canRetry(maxRetries);

        // Assert
        assertThat(canRetry).isFalse();
    }

    // ========== Template Methods Tests ==========

    @Test
    @DisplayName("Should return true when notification uses template")
    void testUsesTemplateReturnsTrue() {
        // Arrange
        Notification notification = builder.templateName("invoice-processed").build();

        // Act
        boolean usesTemplate = notification.usesTemplate();

        // Assert
        assertThat(usesTemplate).isTrue();
    }

    @Test
    @DisplayName("Should return false when notification has no template")
    void testUsesTemplateReturnsFalse() {
        // Arrange
        Notification notification = builder.templateName(null).build();

        // Act
        boolean usesTemplate = notification.usesTemplate();

        // Assert
        assertThat(usesTemplate).isFalse();
    }

    @Test
    @DisplayName("Should return false when template name is empty")
    void testUsesTemplateReturnsFalseForEmptyName() {
        // Arrange
        Notification notification = builder.templateName("").build();

        // Act
        boolean usesTemplate = notification.usesTemplate();

        // Assert
        assertThat(usesTemplate).isFalse();
    }

    @Test
    @DisplayName("Should add template variable")
    void testAddTemplateVariable() {
        // Arrange
        Notification notification = builder.templateVariables(new HashMap<>()).build();

        // Act
        notification.addTemplateVariable("invoiceNumber", "INV-001");
        notification.addTemplateVariable("amount", 1500.00);

        // Assert
        assertThat(notification.getTemplateVariables())
            .containsEntry("invoiceNumber", "INV-001")
            .containsEntry("amount", 1500.00);
    }

    @Test
    @DisplayName("Should initialize template variables map if null")
    void testAddTemplateVariableInitializesMap() {
        // Arrange
        Notification notification = builder.templateVariables(null).build();

        // Act
        notification.addTemplateVariable("key", "value");

        // Assert
        assertThat(notification.getTemplateVariables())
            .isNotNull()
            .containsEntry("key", "value");
    }

    // ========== Metadata Management Tests ==========

    @Test
    @DisplayName("Should add metadata")
    void testAddMetadata() {
        // Arrange
        Notification notification = builder.metadata(new HashMap<>()).build();

        // Act
        notification.addMetadata("priority", "HIGH");
        notification.addMetadata("source", "API");

        // Assert
        assertThat(notification.getMetadata())
            .containsEntry("priority", "HIGH")
            .containsEntry("source", "API");
    }

    @Test
    @DisplayName("Should initialize metadata map if null")
    void testAddMetadataInitializesMap() {
        // Arrange
        Notification notification = builder.metadata(null).build();

        // Act
        notification.addMetadata("key", "value");

        // Assert
        assertThat(notification.getMetadata())
            .isNotNull()
            .containsEntry("key", "value");
    }

    @Test
    @DisplayName("Should clear error message when marking as sent")
    void testMarkSentClearsErrorMessage() {
        // Arrange
        Notification notification = builder
            .status(NotificationStatus.SENDING)
            .errorMessage("Previous error")
            .build();

        // Act
        notification.markSent();

        // Assert
        assertThat(notification.getErrorMessage()).isNull();
    }
}
