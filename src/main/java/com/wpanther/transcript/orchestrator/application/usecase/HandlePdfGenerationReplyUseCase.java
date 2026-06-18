package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.application.dto.event.InboundPdfGenerationReplyEvent;
import com.wpanther.transcript.orchestrator.application.port.out.BatchSigningCommandPort;
import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.domain.model.ItemStatus;
import com.wpanther.transcript.orchestrator.domain.model.SignerRole;
import com.wpanther.transcript.orchestrator.domain.model.SigningFormat;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.BatchRepository;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import com.wpanther.transcript.orchestrator.domain.service.BatchStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Joins a worker reply for a PDF generation command. The state machine inspects
 * the batch's awaitingReplyFor to correlate the reply. After the state machine
 * applies the per-item results and advances the batch, this use case
 * auto-dispatches the next phase: if the batch reached PDF_SIGNING it sends
 * the PAdES signing command for the healthy (PDF_RENDERED) items.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandlePdfGenerationReplyUseCase {

    private final BatchRepository batchRepository;
    private final TranscriptItemRepository itemRepository;
    private final BatchStateMachine stateMachine;
    private final BatchSigningCommandPort signingCommandPort;

    @Transactional
    public void handle(InboundPdfGenerationReplyEvent reply) {
        Batch batch = batchRepository.findByAwaitingReplyFor(reply.getCorrelationId()).orElse(null);
        if (batch == null) {
            log.warn("No batch found for PDF generation reply correlationId={}",
                reply.getCorrelationId());
            return;
        }

        List<TranscriptItem> items = itemRepository.findByBatchId(batch.getId());

        Map<String, String> successByDocId = reply.getItems().stream()
            .filter(InboundPdfGenerationReplyEvent.ItemResult::isGenerated)
            .collect(Collectors.toMap(
                InboundPdfGenerationReplyEvent.ItemResult::getDocumentId,
                InboundPdfGenerationReplyEvent.ItemResult::getPdfUrl));
        Map<String, String> errorByDocId = reply.getItems().stream()
            .filter(r -> !r.isGenerated())
            .collect(Collectors.toMap(
                InboundPdfGenerationReplyEvent.ItemResult::getDocumentId,
                r -> r.getErrorMessage() != null ? r.getErrorMessage() : "PDF generation failed"));

        stateMachine.pdfGenerationReply(batch, items, reply.getCorrelationId(),
            successByDocId, errorByDocId);

        itemRepository.saveAll(items);
        // Reuse the returned domain object: saveAndFlush bumped the @Version and
        // the dispatch below mutates + saves the batch again. Saving the
        // stale-version object a second time would fail optimistic locking.
        batch = batchRepository.save(batch);

        if (batch.getStatus() == BatchStatus.PDF_SIGNING) {
            List<TranscriptItem> healthy = itemRepository.findByBatchIdAndStatusIn(
                batch.getId(), List.of(ItemStatus.PDF_RENDERED));
            signingCommandPort.sendBatchSigningCommand(batch, healthy,
                SignerRole.SEAL, SigningFormat.PDF);
            batchRepository.save(batch);
        }
    }
}
