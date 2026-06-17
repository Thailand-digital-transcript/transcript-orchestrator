package com.wpanther.transcript.orchestrator.domain.repository;

import com.wpanther.transcript.orchestrator.domain.model.ItemStatus;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TranscriptItemRepository {
    TranscriptItem save(TranscriptItem item);
    List<TranscriptItem> saveAll(List<TranscriptItem> items);
    Optional<TranscriptItem> findById(UUID id);
    Optional<TranscriptItem> findByTranscriptId(String transcriptId);
    List<TranscriptItem> findByBatchId(UUID batchId);
    List<TranscriptItem> findByBatchIdAndStatusIn(UUID batchId, List<ItemStatus> statuses);
    Optional<TranscriptItem> findByBatchIdAndDocumentId(UUID batchId, String documentId);
    List<TranscriptItem> findUnassigned(int limit, int offset);
}
