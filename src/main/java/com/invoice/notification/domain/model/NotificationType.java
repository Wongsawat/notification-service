package com.invoice.notification.domain.model;

/**
 * Types of notifications in the invoice processing lifecycle
 */
public enum NotificationType {
    INVOICE_PROCESSED,          // Invoice processing completed
    TAXINVOICE_PROCESSED,       // Tax invoice processing completed
    PDF_GENERATED,              // PDF generation completed
    PDF_SIGNED,                 // PDF signing completed
    DOCUMENT_STORED,            // Document stored successfully
    PROCESSING_FAILED,          // General processing failure
    SYSTEM_ALERT               // System-level alerts
}
