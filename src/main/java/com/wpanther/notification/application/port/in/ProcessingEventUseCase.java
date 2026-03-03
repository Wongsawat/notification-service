package com.wpanther.notification.application.port.in;

import com.wpanther.notification.infrastructure.messaging.EbmsSentEvent;
import com.wpanther.notification.infrastructure.messaging.InvoiceProcessedEvent;
import com.wpanther.notification.infrastructure.messaging.PdfGeneratedEvent;
import com.wpanther.notification.infrastructure.messaging.PdfSignedEvent;
import com.wpanther.notification.infrastructure.messaging.TaxInvoiceProcessedEvent;
import com.wpanther.notification.infrastructure.messaging.XmlSignedEvent;

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
