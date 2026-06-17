package com.wpanther.transcript.orchestrator.application;

import com.wpanther.transcript.orchestrator.application.dto.event.DeanApprovalEvent;
import com.wpanther.transcript.orchestrator.application.port.out.BatchSigningCommandPort;
import com.wpanther.transcript.orchestrator.application.usecase.HandleDeanApprovalUseCase;
import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.domain.repository.*;
import com.wpanther.transcript.orchestrator.domain.service.BatchStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant; import java.util.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HandleDeanApprovalUseCaseTest {
    @Mock BatchRepository batchRepository;
    @Mock TranscriptItemRepository itemRepository;
    @Mock BatchStateMachine stateMachine;
    @Mock BatchSigningCommandPort signingCommandPort;
    @InjectMocks HandleDeanApprovalUseCase useCase;

    @Test void approve_pendingDean_dispatchesDeanCommand() {
        UUID batchId = UUID.randomUUID();
        Batch batch = batchInState(BatchStatus.PENDING_DEAN);
        TranscriptItem item = regSignedItem(batchId);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchIdAndStatusIn(eq(batchId), anyList())).thenReturn(List.of(item));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doAnswer(inv -> { batch.applyDeanApproved("dean01", Instant.now()); return null; })
            .when(stateMachine).deanApprove(any(), any(), any(), any());
        useCase.handle(event(batchId, "APPROVE"));
        verify(signingCommandPort).sendBatchSigningCommand(any(), anyList(),
            eq(SignerRole.DEAN), eq(SigningFormat.XML));
    }
    @Test void approve_alreadyAdvanced_isNoop() {
        UUID batchId = UUID.randomUUID();
        Batch batch = batchInState(BatchStatus.COMPLETED);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        useCase.handle(event(batchId, "APPROVE"));
        verify(signingCommandPort, never()).sendBatchSigningCommand(any(), any(), any(), any());
    }

    private Batch batchInState(BatchStatus s) {
        Batch b = Batch.create("T", "KMUTT", "a");
        switch (s) {
            case PENDING_DEAN -> {
                b.applyClose("a", Instant.now());
                b.applyRegistrarApproved("a", Instant.now());
                b.applyRegistrarSigningComplete();
            }
            case COMPLETED -> {
                // Drive through the full path: PENDING_REGISTRAR -> ... -> COMPLETED
                b.applyClose("a", Instant.now());
                b.applyRegistrarApproved("a", Instant.now());
                b.applyRegistrarSigningComplete();
                b.applyDeanApproved("a", Instant.now());
                b.applyDeanSigningComplete();
                b.applySealingComplete();
                b.applyPdfGenerationComplete();
                b.applyPdfSigningComplete();
            }
            default -> {}
        }
        return b;
    }
    private TranscriptItem regSignedItem(UUID batchId) {
        TranscriptItem i = TranscriptItem.register("tx-1","doc-1","KMUTT","R","k.xml");
        i.assign(batchId); i.markRegistrarSigned("reg.xml"); return i;
    }
    private DeanApprovalEvent event(UUID batchId, String decision) {
        return new DeanApprovalEvent(batchId.toString(), decision, "KMUTT",
            "dean01", Instant.now(), List.of(), null);
    }
}
