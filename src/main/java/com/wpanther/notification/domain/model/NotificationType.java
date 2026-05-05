package com.wpanther.notification.domain.model;

/**
 * Types of notifications in the invoice processing lifecycle
 */
public enum NotificationType {
    RECEIPT_PROCESSED,
    CANCELLATION_NOTE_PROCESSED,
    DEBIT_CREDIT_NOTE_PROCESSED,
    ABBREVIATED_TAX_INVOICE_PROCESSED,
    RECEIPT_PDF_GENERATED,
    CANCELLATION_NOTE_PDF_GENERATED,
    DEBIT_CREDIT_NOTE_PDF_GENERATED,
    ABBREVIATED_TAX_INVOICE_PDF_GENERATED,
    DOCUMENT_ARCHIVED,

    INVOICE_PROCESSED,          // Invoice processing completed
    TAXINVOICE_PROCESSED,       // Tax invoice processing completed
    XML_SIGNED,                 // XML document signed
    PDF_GENERATED,              // Invoice PDF generation completed
    TAX_INVOICE_PDF_GENERATED,  // Tax invoice PDF generation completed
    PDF_SIGNED,                 // PDF signing completed
    DOCUMENT_STORED,            // Document stored successfully
    EBMS_SENT,                  // Document submitted to TRD via ebMS
    PROCESSING_FAILED,          // General processing failure
    SYSTEM_ALERT,              // System-level alerts

    // Saga lifecycle notification types
    SAGA_STARTED,              // Saga orchestration started
    SAGA_STEP_COMPLETED,       // Saga step completed (logged only, no email)
    SAGA_COMPLETED,            // Saga orchestration completed
    SAGA_FAILED                // Saga orchestration failed
}
