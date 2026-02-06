package com.wpanther.notification.domain.model;

/**
 * Types of notifications in the invoice processing lifecycle
 */
public enum NotificationType {
    INVOICE_PROCESSED,          // Invoice processing completed
    TAXINVOICE_PROCESSED,       // Tax invoice processing completed
    PDF_GENERATED,              // PDF generation completed
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
