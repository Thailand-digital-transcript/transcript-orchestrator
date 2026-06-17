package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.transcript.orchestrator.domain.model.ItemStatus;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TranscriptItemRepositoryAdapter implements TranscriptItemRepository {

    private final SpringDataTranscriptItemRepository jpa;

    @Override
    public TranscriptItem save(TranscriptItem item) {
        return jpa.saveAndFlush(TranscriptItemEntity.fromDomain(item)).toDomain();
    }

    @Override
    public List<TranscriptItem> saveAll(List<TranscriptItem> items) {
        List<TranscriptItemEntity> entities = items.stream()
                .map(TranscriptItemEntity::fromDomain)
                .toList();
        jpa.saveAllAndFlush(entities);
        return entities.stream().map(TranscriptItemEntity::toDomain).toList();
    }

    @Override
    public Optional<TranscriptItem> findById(UUID id) {
        return jpa.findById(id).map(TranscriptItemEntity::toDomain);
    }

    @Override
    public Optional<TranscriptItem> findByTranscriptId(String transcriptId) {
        return jpa.findByTranscriptId(transcriptId).map(TranscriptItemEntity::toDomain);
    }

    @Override
    public List<TranscriptItem> findByBatchId(UUID batchId) {
        return jpa.findByBatchId(batchId).stream()
                .map(TranscriptItemEntity::toDomain)
                .toList();
    }

    @Override
    public List<TranscriptItem> findByBatchIdAndStatusIn(UUID batchId, List<ItemStatus> statuses) {
        return jpa.findByBatchIdAndStatusIn(batchId, statuses).stream()
                .map(TranscriptItemEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<TranscriptItem> findByBatchIdAndDocumentId(UUID batchId, String documentId) {
        return jpa.findByBatchIdAndDocumentId(batchId, documentId)
                .map(TranscriptItemEntity::toDomain);
    }

    @Override
    public List<TranscriptItem> findUnassigned(int limit, int offset) {
        return jpa.findByStatusIn(
                        List.of(ItemStatus.REGISTERED),
                        PageRequest.of(offset / limit, limit))
                .stream()
                .map(TranscriptItemEntity::toDomain)
                .toList();
    }
}
