package com.wpanther.notification.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
interface SpringDataDocumentIntakeStatRepository extends JpaRepository<DocumentIntakeStatEntity, UUID> {

    @Query("SELECT e.status, COUNT(e) FROM DocumentIntakeStatEntity e GROUP BY e.status")
    List<Object[]> countGroupByStatus();

    @Query("SELECT e.documentType, COUNT(e) FROM DocumentIntakeStatEntity e GROUP BY e.documentType")
    List<Object[]> countGroupByDocumentType();

    List<DocumentIntakeStatEntity> findByDocumentIdOrderByOccurredAtAsc(String documentId);
}
