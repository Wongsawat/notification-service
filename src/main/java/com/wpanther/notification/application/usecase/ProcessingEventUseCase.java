package com.wpanther.notification.application.usecase;

import com.wpanther.notification.application.port.in.event.AbbreviatedTaxInvoiceProcessedEvent;
import com.wpanther.notification.application.port.in.event.CancellationNoteProcessedEvent;
import com.wpanther.notification.application.port.in.event.DebitCreditNoteProcessedEvent;
import com.wpanther.notification.application.port.in.event.DocumentArchivedEvent;
import com.wpanther.notification.application.port.in.event.EbmsSentEvent;
import com.wpanther.notification.application.port.in.event.InvoiceProcessedEvent;
import com.wpanther.notification.application.port.in.event.InvoicePdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.PdfSignedEvent;
import com.wpanther.notification.application.port.in.event.ReceiptProcessedEvent;
import com.wpanther.notification.application.port.in.event.ReceiptPdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.TaxInvoicePdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.TaxInvoiceProcessedEvent;
import com.wpanther.notification.application.port.in.event.CancellationNotePdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.DebitCreditNotePdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.AbbreviatedTaxInvoicePdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.XmlSignedEvent;

/**
 * Input port: use case for handling invoice/PDF/XML processing completion events.
 * Implemented by {@link com.wpanther.notification.application.service.NotificationService}.
 *
 * <p>These events trigger email notifications to the configured default recipient.</p>
 */
public interface ProcessingEventUseCase {

    void handleInvoiceProcessed(InvoiceProcessedEvent event);

    void handleTaxInvoiceProcessed(TaxInvoiceProcessedEvent event);

    void handleInvoicePdfGenerated(InvoicePdfGeneratedEvent event);

    void handleTaxInvoicePdfGenerated(TaxInvoicePdfGeneratedEvent event);

    void handlePdfSigned(PdfSignedEvent event);

    void handleXmlSigned(XmlSignedEvent event);

    void handleEbmsSent(EbmsSentEvent event);

    void handleReceiptProcessed(ReceiptProcessedEvent event);

    void handleCancellationNoteProcessed(CancellationNoteProcessedEvent event);

    void handleDebitCreditNoteProcessed(DebitCreditNoteProcessedEvent event);

    void handleAbbreviatedTaxInvoiceProcessed(AbbreviatedTaxInvoiceProcessedEvent event);

    void handleReceiptPdfGenerated(ReceiptPdfGeneratedEvent event);

    void handleCancellationNotePdfGenerated(CancellationNotePdfGeneratedEvent event);

    void handleDebitCreditNotePdfGenerated(DebitCreditNotePdfGeneratedEvent event);

    void handleAbbreviatedTaxInvoicePdfGenerated(AbbreviatedTaxInvoicePdfGeneratedEvent event);

    void handleDocumentArchived(DocumentArchivedEvent event);
}
