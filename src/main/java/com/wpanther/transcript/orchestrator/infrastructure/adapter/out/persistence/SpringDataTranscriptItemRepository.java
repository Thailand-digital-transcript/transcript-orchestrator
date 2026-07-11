package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.transcript.orchestrator.domain.model.ItemStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataTranscriptItemRepository extends JpaRepository<TranscriptItemEntity, UUID> {

    Optional<TranscriptItemEntity> findByTranscriptId(String transcriptId);

    List<TranscriptItemEntity> findByBatchId(UUID batchId);

    List<TranscriptItemEntity> findByBatchIdAndStatusIn(UUID batchId, List<ItemStatus> statuses);

    Optional<TranscriptItemEntity> findByBatchIdAndDocumentId(UUID batchId, String documentId);

    List<TranscriptItemEntity> findByStatusIn(List<ItemStatus> statuses, Pageable pageable);

    /**
     * Terminal items whose intermediate artifacts have not been reclaimed yet and which have
     * been dead longer than the retention grace period. Oldest first, so a backlog drains in
     * a stable order rather than starving the earliest failures.
     */
    @Query("""
           SELECT i FROM TranscriptItemEntity i
            WHERE i.artifactsPurgedAt IS NULL
              AND i.status IN (com.wpanther.transcript.orchestrator.domain.model.ItemStatus.FAILED,
                               com.wpanther.transcript.orchestrator.domain.model.ItemStatus.REJECTED)
              AND i.updatedAt < :deadBefore
            ORDER BY i.updatedAt ASC
           """)
    List<TranscriptItemEntity> findTerminalWithUnpurgedArtifacts(
            @Param("deadBefore") Instant deadBefore, Pageable pageable);
}
