package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.application.dto.event.InboundBatchSigningReplyEvent;
import com.wpanther.transcript.orchestrator.application.port.out.BatchCompletedEventPort;
import com.wpanther.transcript.orchestrator.application.port.out.BatchSigningCommandPort;
import com.wpanther.transcript.orchestrator.application.port.out.PdfGenerationCommandPort;
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
 * Joins a worker reply for any XAdES signing phase (REGISTRAR / DEAN / SEAL)
 * or the PDF PAdES signing phase. The state machine inspects the batch's
 * current status to know which phase just completed. The use case then
 * auto-dispatches the next phase: PENDING_DEAN is a human gate, SEALING
 * triggers the seal signing command, PDF_GENERATION triggers the render
 * command, and COMPLETED publishes the terminal event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandleSigningReplyUseCase {

    private final BatchRepository batchRepository;
    private final TranscriptItemRepository itemRepository;
    private final BatchStateMachine stateMachine;
    private final BatchSigningCommandPort signingCommandPort;
    private final PdfGenerationCommandPort pdfCommandPort;
    private final BatchCompletedEventPort completedPort;

    @Transactional
    public void handle(InboundBatchSigningReplyEvent reply) {
        Batch batch = batchRepository.findByAwaitingReplyFor(reply.getCorrelationId()).orElse(null);
        if (batch == null) {
            log.warn("No batch found for signing reply correlationId={}", reply.getCorrelationId());
            return;
        }

        List<TranscriptItem> items = itemRepository.findByBatchId(batch.getId());
        BatchStatus beforeStatus = batch.getStatus();

        Map<String, String> successByDocId = reply.getItems().stream()
            .filter(InboundBatchSigningReplyEvent.ItemResult::isSigned)
            .collect(Collectors.toMap(
                InboundBatchSigningReplyEvent.ItemResult::getDocumentId,
                InboundBatchSigningReplyEvent.ItemResult::getSignedDocUrl));
        Map<String, String> errorByDocId = reply.getItems().stream()
            .filter(r -> !r.isSigned())
            .collect(Collectors.toMap(
                InboundBatchSigningReplyEvent.ItemResult::getDocumentId,
                r -> r.getErrorMessage() != null ? r.getErrorMessage() : "Signing failed"));

        stateMachine.signingReply(batch, items, reply.getCorrelationId(), successByDocId, errorByDocId);

        itemRepository.saveAll(items);
        // Reuse the returned domain object: saveAndFlush bumped the @Version and
        // dispatchNextPhase may mutate + save the batch again. Passing the
        // stale-version object on would fail optimistic locking.
        batch = batchRepository.save(batch);

        dispatchNextPhase(batch, beforeStatus, items);
    }

    private void dispatchNextPhase(Batch batch, BatchStatus beforeStatus, List<TranscriptItem> items) {
        BatchStatus current = batch.getStatus();
        switch (current) {
            case PENDING_DEAN -> log.info("Batch {} at PENDING_DEAN — awaiting dean approval", batch.getId());
            case SEALING -> {
                List<TranscriptItem> healthy = itemRepository.findByBatchIdAndStatusIn(
                    batch.getId(), List.of(ItemStatus.DEAN_SIGNED));
                signingCommandPort.sendBatchSigningCommand(batch, healthy,
                    SignerRole.SEAL, SigningFormat.XML);
                batchRepository.save(batch);
            }
            case PDF_GENERATION -> {
                List<TranscriptItem> healthy = itemRepository.findByBatchIdAndStatusIn(
                    batch.getId(), List.of(ItemStatus.SEALED));
                pdfCommandPort.sendPdfGenerationCommand(batch, healthy);
                batchRepository.save(batch);
            }
            case COMPLETED -> completedPort.publishBatchCompleted(batch);
            default -> log.debug("Batch {} advanced {} → {} — no auto-dispatch",
                batch.getId(), beforeStatus, current);
        }
    }
}
