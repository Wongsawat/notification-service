package com.wpanther.notification.application.usecase;

import com.wpanther.notification.application.dto.event.EbmsSentEvent;
import com.wpanther.notification.application.dto.event.InvoiceProcessedEvent;
import com.wpanther.notification.application.dto.event.PdfGeneratedEvent;
import com.wpanther.notification.application.dto.event.PdfSignedEvent;
import com.wpanther.notification.application.dto.event.TaxInvoiceProcessedEvent;
import com.wpanther.notification.application.dto.event.XmlSignedEvent;

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

    void handlePdfSigned(PdfSignedEvent event);

    void handleXmlSigned(XmlSignedEvent event);

    void handleEbmsSent(EbmsSentEvent event);
}
