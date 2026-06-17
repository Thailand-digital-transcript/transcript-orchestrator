package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.domain.exception.*;
import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class AssignItemsUseCase {
    private final BatchRepository batchRepository;
    private final TranscriptItemRepository itemRepository;

    @Transactional
    public void assign(UUID batchId, List<UUID> itemIds) {
        Batch batch = batchRepository.findById(batchId)
            .orElseThrow(() -> new BatchNotFoundException(batchId.toString()));
        if (batch.getStatus() != BatchStatus.DRAFT)
            throw new InvalidBatchStateException("Cannot assign items to batch in state " + batch.getStatus());
        for (UUID itemId : itemIds) {
            TranscriptItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
            // TranscriptItem.assign() throws IllegalStateException if xmlStorageKey is null
            item.assign(batchId);
            itemRepository.save(item);
            batch.incrementItemCount();
        }
        batchRepository.save(batch);
    }
}
