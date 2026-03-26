package com.wpanther.notification.application.usecase;

import com.wpanther.notification.application.port.in.event.EbmsSentEvent;
import com.wpanther.notification.application.port.in.event.InvoiceProcessedEvent;
import com.wpanther.notification.application.port.in.event.PdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.PdfSignedEvent;
import com.wpanther.notification.application.port.in.event.TaxInvoicePdfGeneratedEvent;
import com.wpanther.notification.application.port.in.event.TaxInvoiceProcessedEvent;
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

    void handlePdfGenerated(PdfGeneratedEvent event);

    void handleTaxInvoicePdfGenerated(TaxInvoicePdfGeneratedEvent event);

    void handlePdfSigned(PdfSignedEvent event);

    void handleXmlSigned(XmlSignedEvent event);

    void handleEbmsSent(EbmsSentEvent event);
}
