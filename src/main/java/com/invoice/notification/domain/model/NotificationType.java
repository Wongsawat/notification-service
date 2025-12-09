package com.invoice.notification.domain.model;

/**
 * Types of notifications in the invoice processing lifecycle
 */
public enum NotificationType {
    INVOICE_RECEIVED,           // Invoice intake completed
    INVOICE_VALIDATION_FAILED,  // Validation errors
    INVOICE_PROCESSED,          // Invoice processing completed
    PDF_GENERATED,              // PDF generation completed
    DOCUMENT_STORED,            // Document stored successfully
    PROCESSING_FAILED,          // General processing failure
    SYSTEM_ALERT               // System-level alerts
}
