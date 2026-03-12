package com.wpanther.notification.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter implementation of OutboxEventRepository from saga-commons.
 * Bridges between domain OutboxEvent and JPA OutboxEventEntity.
 *
 * This component is automatically detected by saga-commons auto-configuration,
 * which creates the OutboxService bean for reliable event publishing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JpaOutboxEventRepository implements OutboxEventRepository {

    private final SpringDataOutboxRepository springRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        log.debug("Saving outbox event: id={}, type={}, aggregateId={}",
            event.getId(), event.getEventType(), event.getAggregateId());

        OutboxEventEntity entity = OutboxEventEntity.fromDomain(event);
        OutboxEventEntity saved = springRepository.save(entity);

        log.debug("Saved outbox event: id={}, status={}", saved.getId(), saved.getStatus());
        return saved.toDomain();
    }

    @Override
    public Optional<OutboxEvent> findById(UUID id) {
        log.debug("Finding outbox event by id: {}", id);
        return springRepository.findById(id)
            .map(OutboxEventEntity::toDomain);
    }

    @Override
    public List<OutboxEvent> findPendingEvents(int limit) {
        log.debug("Finding pending outbox events, limit={}", limit);

        List<OutboxEventEntity> entities = springRepository.findByStatusOrderByCreatedAtAsc(
            OutboxStatus.PENDING, Pageable.ofSize(limit));

        log.debug("Found {} pending outbox events", entities.size());
        return entities.stream()
            .map(OutboxEventEntity::toDomain)
            .toList();
    }

    @Override
    public List<OutboxEvent> findFailedEvents(int limit) {
        log.debug("Finding failed outbox events for retry, limit={}", limit);

        List<OutboxEventEntity> entities = springRepository.findFailedEventsOrderByCreatedAtAsc(
            Pageable.ofSize(limit));

        log.debug("Found {} failed outbox events", entities.size());
        return entities.stream()
            .map(OutboxEventEntity::toDomain)
            .toList();
    }

    @Override
    public List<OutboxEvent> findByAggregate(String aggregateType, String aggregateId) {
        log.debug("Finding outbox events for aggregate: type={}, id={}", aggregateType, aggregateId);

        List<OutboxEventEntity> entities = springRepository
            .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(aggregateType, aggregateId);

        log.debug("Found {} events for aggregate", entities.size());
        return entities.stream()
            .map(OutboxEventEntity::toDomain)
            .toList();
    }

    @Override
    public int deletePublishedBefore(Instant cutoffTime) {
        log.debug("Deleting published outbox events before: {}", cutoffTime);

        int deleted = springRepository.deletePublishedBefore(cutoffTime);

        log.info("Deleted {} published outbox events before {}", deleted, cutoffTime);
        return deleted;
    }
}
