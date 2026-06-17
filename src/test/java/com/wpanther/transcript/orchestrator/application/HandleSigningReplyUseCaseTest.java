package com.wpanther.transcript.orchestrator.application;

import com.wpanther.transcript.orchestrator.application.dto.event.InboundBatchSigningReplyEvent;
import com.wpanther.transcript.orchestrator.application.dto.event.InboundBatchSigningReplyEvent.ItemResult;
import com.wpanther.transcript.orchestrator.application.port.out.BatchCompletedEventPort;
import com.wpanther.transcript.orchestrator.application.port.out.BatchSigningCommandPort;
import com.wpanther.transcript.orchestrator.application.port.out.PdfGenerationCommandPort;
import com.wpanther.transcript.orchestrator.application.usecase.HandleSigningReplyUseCase;
import com.wpanther.transcript.orchestrator.domain.model.Batch;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandleSigningReplyUseCaseTest {
    @Mock BatchRepository batchRepository;
    @Mock TranscriptItemRepository itemRepository;
    @Mock BatchStateMachine stateMachine;
    @Mock BatchSigningCommandPort signingCommandPort;
    @Mock PdfGenerationCommandPort pdfCommandPort;
    @Mock BatchCompletedEventPort completedPort;
    @InjectMocks HandleSigningReplyUseCase useCase;

    @Test
    void registrarSigningReply_success_transitionsToPendingDean() {
        Batch batch = batchInRegistrarSigning();
        UUID batchId = batch.getId();
        when(batchRepository.findByAwaitingReplyFor("corr-1")).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchId(batchId)).thenReturn(List.of(item(batchId, "doc-1")));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> { batch.applyRegistrarSigningComplete(); return null; })
            .when(stateMachine).signingReply(any(), any(), any(), any(), any());

        useCase.handle(successReply("corr-1", batchId, "doc-1", "signed.xml"));

        verify(batchRepository).save(batch);
        verify(signingCommandPort, never()).sendBatchSigningCommand(any(), any(), any(), any());
        verify(pdfCommandPort, never()).sendPdfGenerationCommand(any(), any());
    }

    @Test
    void sealingComplete_transitionsToPdfGeneration_andDispatches() {
        Batch batch = batchInSealing();
        UUID batchId = batch.getId();
        TranscriptItem sealedItem = item(batchId, "doc-1");
        when(batchRepository.findByAwaitingReplyFor("corr-s")).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchId(batchId)).thenReturn(List.of(sealedItem));
        when(itemRepository.findByBatchIdAndStatusIn(eq(batchId), anyList()))
            .thenReturn(List.of(sealedItem));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> { batch.applySealingComplete(); return null; })
            .when(stateMachine).signingReply(any(), any(), any(), any(), any());

        useCase.handle(successReply("corr-s", batchId, "doc-1", "sealed.xml"));

        verify(pdfCommandPort).sendPdfGenerationCommand(eq(batch), anyList());
    }

    @Test
    void pdfSigningComplete_transitionsToCompleted_andPublishesEvent() {
        Batch batch = batchInPdfSigning();
        UUID batchId = batch.getId();
        when(batchRepository.findByAwaitingReplyFor("corr-p")).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchId(batchId)).thenReturn(List.of(item(batchId, "doc-1")));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> { batch.applyPdfSigningComplete(); return null; })
            .when(stateMachine).signingReply(any(), any(), any(), any(), any());

        useCase.handle(successReply("corr-p", batchId, "doc-1", "signed.pdf"));

        verify(completedPort).publishBatchCompleted(batch);
    }

    @Test
    void unknownCorrelationId_isLoggedAndIgnored() {
        when(batchRepository.findByAwaitingReplyFor("unknown")).thenReturn(Optional.empty());

        assertThatCode(() -> useCase.handle(successReply("unknown", UUID.randomUUID(), "d", "k")))
            .doesNotThrowAnyException();
    }

    private Batch batchInRegistrarSigning() {
        Batch b = Batch.create("T", "KMUTT", "admin");
        b.applyClose("admin", Instant.now());
        b.applyRegistrarApproved("admin", Instant.now());
        b.applySigningStarted("corr-1");
        return b;
    }

    private Batch batchInSealing() {
        Batch b = Batch.create("T", "KMUTT", "admin");
        b.applyClose("admin", Instant.now());
        b.applyRegistrarApproved("admin", Instant.now());
        b.applyRegistrarSigningComplete();
        b.applyDeanApproved("admin", Instant.now());
        b.applyDeanSigningComplete();
        b.applySigningStarted("corr-s");
        return b;
    }

    private Batch batchInPdfSigning() {
        Batch b = Batch.create("T", "KMUTT", "admin");
        b.applyClose("admin", Instant.now());
        b.applyRegistrarApproved("admin", Instant.now());
        b.applyRegistrarSigningComplete();
        b.applyDeanApproved("admin", Instant.now());
        b.applyDeanSigningComplete();
        b.applySealingComplete();
        b.applyPdfGenerationComplete();
        b.applySigningStarted("corr-p");
        return b;
    }

    private TranscriptItem item(UUID batchId, String docId) {
        TranscriptItem i = TranscriptItem.register("tx-" + docId, docId, "KMUTT", "R", docId + ".xml");
        i.assign(batchId);
        return i;
    }

    private InboundBatchSigningReplyEvent successReply(String corrId, UUID batchId, String docId, String signedKey) {
        return new InboundBatchSigningReplyEvent(
            batchId.toString(), "sign-xml", corrId, "SUCCESS", null, batchId.toString(),
            List.of(new ItemResult(docId, "SIGNED", signedKey, 5000L, null)));
    }
}
