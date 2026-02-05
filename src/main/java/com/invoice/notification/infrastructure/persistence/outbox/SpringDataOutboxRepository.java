package com.invoice.notification.infrastructure.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for OutboxEventEntity.
 * Provides CRUD operations and custom queries for outbox pattern.
 */
@Repository
public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Find events by status, ordered by creation time (FIFO).
     * Used by OutboxService to poll pending events for publication.
     *
     * @param status OutboxStatus to filter by
     * @param pageable Pagination parameters (limit)
     * @return List of events matching status
     */
    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    /**
     * Find failed events for retry processing.
     * Uses explicit JPQL query for clarity.
     *
     * @param pageable Pagination parameters (limit)
     * @return List of failed events ordered by creation time
     */
    @Query("SELECT e FROM OutboxEventEntity e WHERE e.status = 'FAILED' ORDER BY e.createdAt ASC")
    List<OutboxEventEntity> findFailedEventsOrderByCreatedAtAsc(Pageable pageable);

    /**
     * Find all events for a specific aggregate, ordered chronologically.
     * Useful for event sourcing or debugging.
     *
     * @param aggregateType Aggregate type (e.g., "Notification")
     * @param aggregateId Aggregate ID (e.g., notification UUID)
     * @return List of events for the aggregate
     */
    List<OutboxEventEntity> findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
        String aggregateType, String aggregateId);

    /**
     * Delete published events older than specified time.
     * Used for outbox table cleanup to prevent unbounded growth.
     *
     * @param before Cutoff time (events published before this will be deleted)
     * @return Number of events deleted
     */
    @Modifying
    @Query("DELETE FROM OutboxEventEntity e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
