package com.wpanther.notification.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationType Enum Tests")
class NotificationTypeTest {

    @Test
    @DisplayName("Should have exactly 23 type values")
    void testEnumCount() {
        assertThat(NotificationType.values()).hasSize(23);
    }

    @Test
    @DisplayName("Should contain all expected type values")
    void testEnumValues() {
        assertThat(NotificationType.values())
            .containsExactlyInAnyOrder(
                NotificationType.RECEIPT_PROCESSED,
                NotificationType.CANCELLATION_NOTE_PROCESSED,
                NotificationType.DEBIT_CREDIT_NOTE_PROCESSED,
                NotificationType.ABBREVIATED_TAX_INVOICE_PROCESSED,
                NotificationType.RECEIPT_PDF_GENERATED,
                NotificationType.CANCELLATION_NOTE_PDF_GENERATED,
                NotificationType.DEBIT_CREDIT_NOTE_PDF_GENERATED,
                NotificationType.ABBREVIATED_TAX_INVOICE_PDF_GENERATED,
                NotificationType.DOCUMENT_ARCHIVED,
                NotificationType.INVOICE_PROCESSED,
                NotificationType.TAXINVOICE_PROCESSED,
                NotificationType.XML_SIGNED,
                NotificationType.PDF_GENERATED,
                NotificationType.TAX_INVOICE_PDF_GENERATED,
                NotificationType.PDF_SIGNED,
                NotificationType.DOCUMENT_STORED,
                NotificationType.EBMS_SENT,
                NotificationType.PROCESSING_FAILED,
                NotificationType.SYSTEM_ALERT,
                NotificationType.SAGA_STARTED,
                NotificationType.SAGA_STEP_COMPLETED,
                NotificationType.SAGA_COMPLETED,
                NotificationType.SAGA_FAILED
            );
    }

    @Test
    @DisplayName("Should convert enum to string correctly")
    void testEnumToString() {
        assertThat(NotificationType.INVOICE_PROCESSED.toString()).isEqualTo("INVOICE_PROCESSED");
        assertThat(NotificationType.TAXINVOICE_PROCESSED.toString()).isEqualTo("TAXINVOICE_PROCESSED");
        assertThat(NotificationType.PDF_GENERATED.toString()).isEqualTo("PDF_GENERATED");
        assertThat(NotificationType.PDF_SIGNED.toString()).isEqualTo("PDF_SIGNED");
        assertThat(NotificationType.SAGA_COMPLETED.toString()).isEqualTo("SAGA_COMPLETED");
        assertThat(NotificationType.SAGA_FAILED.toString()).isEqualTo("SAGA_FAILED");
    }
}
