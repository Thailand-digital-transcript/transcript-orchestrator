package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.application.port.out.ArtifactStoragePort;
import com.wpanther.transcript.orchestrator.domain.model.StorageRef;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import com.wpanther.transcript.orchestrator.domain.service.TranscriptKeyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reclaims one dead item's intermediate artifacts. Runs in its own REQUIRES_NEW transaction
 * so a failure on one item does not roll back another — same shape as
 * {@link StuckPhaseSweepTask}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanedArtifactPurgeTask {

    private final TranscriptItemRepository itemRepository;
    private final ArtifactStoragePort artifactStorage;
    private final TranscriptKeyResolver resolver;

    /**
     * Deletes before marking. If the delete succeeds and the commit then fails, the next
     * sweep retries the delete — which is a no-op on an absent key, so the item still ends
     * up purged. Marking first would risk the opposite: a row claiming the objects were
     * reclaimed while they are still sitting in the bucket, invisible to every later sweep.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purge(TranscriptItem item) {
        List<StorageRef> refs = item.purgeableArtifactRefs(resolver);
        for (StorageRef ref : refs) {
            artifactStorage.delete(ref);
        }
        item.markArtifactsPurged();
        itemRepository.save(item);
        log.info("Purged {} orphaned artifact(s) for terminal item {} (status={})",
                refs.size(), item.getTranscriptId(), item.getStatus());
    }
}
