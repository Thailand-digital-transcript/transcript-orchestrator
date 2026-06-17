package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.domain.exception.BatchNotFoundException;
import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.domain.repository.*;
import com.wpanther.transcript.orchestrator.domain.service.BatchStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class CloseBatchUseCase {
    private final BatchRepository batchRepository;
    private final TranscriptItemRepository itemRepository;
    private final BatchStateMachine stateMachine;

    @Transactional
    public Batch close(UUID batchId, String closedBy) {
        Batch batch = batchRepository.findById(batchId)
            .orElseThrow(() -> new BatchNotFoundException(batchId.toString()));
        List<TranscriptItem> items = itemRepository.findByBatchIdAndStatusIn(batchId,
            List.of(ItemStatus.ASSIGNED));
        stateMachine.close(batch, items, closedBy);
        return batchRepository.save(batch);
    }
}
