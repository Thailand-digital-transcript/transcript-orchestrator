package com.wpanther.transcript.orchestrator.application;

import com.wpanther.transcript.orchestrator.application.dto.event.InboundPdfGenerationReplyEvent;
import com.wpanther.transcript.orchestrator.application.dto.event.InboundPdfGenerationReplyEvent.ItemResult;
import com.wpanther.transcript.orchestrator.application.port.out.BatchSigningCommandPort;
import com.wpanther.transcript.orchestrator.application.usecase.HandlePdfGenerationReplyUseCase;
import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.model.SignerRole;
import com.wpanther.transcript.orchestrator.domain.model.SigningFormat;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.BatchRepository;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import com.wpanther.transcript.orchestrator.domain.service.BatchStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandlePdfGenerationReplyUseCaseTest {
    @Mock BatchRepository batchRepository;
    @Mock TranscriptItemRepository itemRepository;
    @Mock BatchStateMachine stateMachine;
    @Mock BatchSigningCommandPort signingCommandPort;
    @InjectMocks HandlePdfGenerationReplyUseCase useCase;

    @Test
    void success_transitionsToPdfSigning_andDispatchesSigningCommand() {
        Batch batch = batchInPdfGeneration();
        UUID batchId = batch.getId();
        TranscriptItem item = sealedItem(batchId, "doc-1");
        when(batchRepository.findByAwaitingReplyFor("corr-pdf")).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchId(batchId)).thenReturn(List.of(item));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.findByBatchIdAndStatusIn(eq(batchId), anyList())).thenReturn(List.of(item));
        doAnswer(inv -> { batch.applyPdfGenerationComplete(); return null; })
            .when(stateMachine).pdfGenerationReply(any(), any(), any(), any(), any());

        useCase.handle(reply("corr-pdf", batchId, "doc-1", "doc.pdf"));

        verify(signingCommandPort).sendBatchSigningCommand(eq(batch), anyList(),
            eq(SignerRole.SEAL), eq(SigningFormat.PDF));
    }

    @Test
    void unknownCorrelationId_isIgnored() {
        when(batchRepository.findByAwaitingReplyFor("unk")).thenReturn(Optional.empty());

        assertThatCode(() -> useCase.handle(reply("unk", UUID.randomUUID(), "d", "k")))
            .doesNotThrowAnyException();
    }

    private Batch batchInPdfGeneration() {
        Batch b = Batch.create("T", "KMUTT", "a");
        b.applyClose("a", Instant.now());
        b.applyRegistrarApproved("a", Instant.now());
        b.applyRegistrarSigningComplete();
        b.applyDeanApproved("a", Instant.now());
        b.applyDeanSigningComplete();
        b.applySealingComplete();
        b.applyPdfGenerationStarted("corr-pdf");
        return b;
    }

    private TranscriptItem sealedItem(UUID batchId, String docId) {
        TranscriptItem i = TranscriptItem.register("tx-" + docId, docId, "KMUTT", "R", docId + ".xml");
        i.assign(batchId);
        i.markRegistrarSigned("r.xml");
        i.markDeanSigned("d.xml");
        i.markSealed("s.xml");
        return i;
    }

    private InboundPdfGenerationReplyEvent reply(String corrId, UUID batchId, String docId, String key) {
        return new InboundPdfGenerationReplyEvent(
            batchId.toString(), "generate-transcript-pdf", corrId, "SUCCESS", null,
            batchId.toString(), List.of(new ItemResult(docId, "GENERATED", key, 50000L, null)));
    }
}
