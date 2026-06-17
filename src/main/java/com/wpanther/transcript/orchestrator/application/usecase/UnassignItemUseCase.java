package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.domain.exception.*;
import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class UnassignItemUseCase {
    private final BatchRepository batchRepository;
    private final TranscriptItemRepository itemRepository;

    @Transactional
    public void unassign(UUID batchId, UUID itemId) {
        Batch batch = batchRepository.findById(batchId)
            .orElseThrow(() -> new BatchNotFoundException(batchId.toString()));
        if (batch.getStatus() != BatchStatus.DRAFT)
            throw new InvalidBatchStateException("Cannot unassign items from batch in state " + batch.getStatus());
        TranscriptItem item = itemRepository.findById(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
        item.unassign();
        itemRepository.save(item);
        batch.decrementItemCount();
        batchRepository.save(batch);
    }
}
