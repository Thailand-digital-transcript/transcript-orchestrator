package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.transcript.orchestrator.domain.model.ItemStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataTranscriptItemRepository extends JpaRepository<TranscriptItemEntity, UUID> {

    Optional<TranscriptItemEntity> findByTranscriptId(String transcriptId);

    List<TranscriptItemEntity> findByBatchId(UUID batchId);

    List<TranscriptItemEntity> findByBatchIdAndStatusIn(UUID batchId, List<ItemStatus> statuses);

    Optional<TranscriptItemEntity> findByBatchIdAndDocumentId(UUID batchId, String documentId);

    List<TranscriptItemEntity> findByStatusIn(List<ItemStatus> statuses, Pageable pageable);
}
