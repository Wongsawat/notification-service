package com.wpanther.notification.domain.repository;

import com.wpanther.notification.domain.model.DocumentIntakeStat;

import java.util.List;
import java.util.Map;

public interface DocumentIntakeStatRepository {

    DocumentIntakeStat save(DocumentIntakeStat stat);

    /** Returns a map of status → count for all rows. */
    Map<String, Long> countByStatus();

    /** Returns a map of documentType → count for all rows. */
    Map<String, Long> countByDocumentType();

    /** Returns all stats for a document ordered by occurredAt ascending. */
    List<DocumentIntakeStat> findByDocumentId(String documentId);
}