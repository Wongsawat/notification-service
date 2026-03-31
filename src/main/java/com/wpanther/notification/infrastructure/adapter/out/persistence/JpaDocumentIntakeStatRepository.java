package com.wpanther.notification.infrastructure.adapter.out.persistence;

import com.wpanther.notification.domain.model.DocumentIntakeStat;
import com.wpanther.notification.domain.repository.DocumentIntakeStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JpaDocumentIntakeStatRepository implements DocumentIntakeStatRepository {

    private final SpringDataDocumentIntakeStatRepository springDataRepo;

    @Override
    public DocumentIntakeStat save(DocumentIntakeStat stat) {
        DocumentIntakeStatEntity entity = toEntity(stat);
        DocumentIntakeStatEntity saved = springDataRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Map<String, Long> countByStatus() {
        return springDataRepo.countGroupByStatus().stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> (Long) row[1]
            ));
    }

    @Override
    public Map<String, Long> countByDocumentType() {
        return springDataRepo.countGroupByDocumentType().stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> (Long) row[1]
            ));
    }

    @Override
    public List<DocumentIntakeStat> findByDocumentId(String documentId) {
        return springDataRepo.findByDocumentIdOrderByOccurredAtAsc(documentId).stream()
            .map(this::toDomain)
            .toList();
    }

    private DocumentIntakeStatEntity toEntity(DocumentIntakeStat stat) {
        return DocumentIntakeStatEntity.builder()
            .id(stat.getId())
            .documentId(stat.getDocumentId())
            .documentType(stat.getDocumentType())
            .documentNumber(stat.getDocumentNumber())
            .status(stat.getStatus())
            .source(stat.getSource())
            .correlationId(stat.getCorrelationId())
            .occurredAt(stat.getOccurredAt())
            .build();
    }

    private DocumentIntakeStat toDomain(DocumentIntakeStatEntity entity) {
        return DocumentIntakeStat.builder()
            .id(entity.getId())
            .documentId(entity.getDocumentId())
            .documentType(entity.getDocumentType())
            .documentNumber(entity.getDocumentNumber())
            .status(entity.getStatus())
            .source(entity.getSource())
            .correlationId(entity.getCorrelationId())
            .occurredAt(entity.getOccurredAt())
            .build();
    }
}
