package com.wpanther.notification.application.usecase;

import com.wpanther.notification.application.dto.DocumentIntakeStatsResponse;
import com.wpanther.notification.application.port.in.event.DocumentReceivedTraceEvent;
import com.wpanther.notification.domain.model.DocumentIntakeStat;

import java.util.List;

/**
 * Input port: persist document intake status events as statistics rows.
 * Implemented by NotificationService.
 */
public interface DocumentIntakeStatUseCase {

    void handleIntakeStat(DocumentReceivedTraceEvent event);

    DocumentIntakeStatsResponse getIntakeStats();

    List<DocumentIntakeStat> getStatsByDocumentId(String documentId);
}
