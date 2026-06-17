package com.wpanther.transcript.orchestrator.application;

import com.wpanther.transcript.orchestrator.application.dto.event.RegistrarApprovalEvent;
import com.wpanther.transcript.orchestrator.application.port.out.BatchSigningCommandPort;
import com.wpanther.transcript.orchestrator.application.usecase.HandleRegistrarApprovalUseCase;
import com.wpanther.transcript.orchestrator.domain.exception.*;
import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.domain.repository.*;
import com.wpanther.transcript.orchestrator.domain.service.BatchStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant; import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HandleRegistrarApprovalUseCaseTest {
    @Mock BatchRepository batchRepository;
    @Mock TranscriptItemRepository itemRepository;
    @Mock BatchStateMachine stateMachine;
    @Mock BatchSigningCommandPort signingCommandPort;
    @InjectMocks HandleRegistrarApprovalUseCase useCase;

    @Test void approve_validBatch_dispatches() {
        UUID batchId = UUID.randomUUID();
        Batch batch = batchInState(batchId, BatchStatus.PENDING_REGISTRAR);
        TranscriptItem item = assignedItem(batchId);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchIdAndStatusIn(eq(batchId), anyList())).thenReturn(List.of(item));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase.handle(approvalEvent(batchId, "APPROVE", List.of(), null));
        verify(stateMachine).registrarApprove(eq(batch), eq("KMUTT"), any(), any());
        verify(signingCommandPort).sendBatchSigningCommand(any(), anyList(),
            eq(SignerRole.REGISTRAR), eq(SigningFormat.XML));
    }
    @Test void approve_batchNotFound_throws() {
        UUID batchId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.handle(approvalEvent(batchId, "APPROVE", List.of(), null)))
            .isInstanceOf(BatchNotFoundException.class);
    }
    @Test void reject_cancelsBatch_noSigningCommand() {
        UUID batchId = UUID.randomUUID();
        Batch batch = batchInState(batchId, BatchStatus.PENDING_REGISTRAR);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase.handle(approvalEvent(batchId, "REJECT", null, "Not ready"));
        verify(stateMachine).registrarReject(eq(batch), eq("KMUTT"), any(), any(), eq("Not ready"));
        verify(signingCommandPort, never()).sendBatchSigningCommand(any(), any(), any(), any());
    }
    @Test void approve_withSubsetRejected_cancelled_noSigningCommand() {
        UUID batchId = UUID.randomUUID();
        Batch batch = batchInState(batchId, BatchStatus.PENDING_REGISTRAR);
        TranscriptItem item = assignedItem(batchId);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchIdAndStatusIn(eq(batchId), anyList())).thenReturn(List.of(item));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stateMachine.applyItemRejections(any(), any(), any(), any(), any(), any())).thenReturn(true);
        useCase.handle(approvalEvent(batchId, "APPROVE", List.of(item.getDocumentId()), null));
        verify(signingCommandPort, never()).sendBatchSigningCommand(any(), any(), any(), any());
    }

    private Batch batchInState(UUID id, BatchStatus s) {
        Batch b = Batch.create("T", "KMUTT", "admin");
        if (s != BatchStatus.DRAFT) {
            // applyClose -> PENDING_REGISTRAR
            b.applyClose("admin", Instant.now());
        }
        if (s == BatchStatus.REGISTRAR_SIGNING) {
            b.applyRegistrarApproved("admin", Instant.now());
        } else if (s == BatchStatus.PENDING_DEAN) {
            b.applyRegistrarApproved("admin", Instant.now());
            b.applyRegistrarSigningComplete();
        }
        return b;
    }
    private TranscriptItem assignedItem(UUID batchId) {
        TranscriptItem i = TranscriptItem.register("tx-1","doc-1","KMUTT","R","k.xml");
        i.assign(batchId); return i;
    }
    private RegistrarApprovalEvent approvalEvent(UUID batchId, String decision,
            List<String> rejected, String reason) {
        return new RegistrarApprovalEvent(batchId.toString(), decision, "KMUTT",
            "reg01", Instant.now(), rejected, reason);
    }
}
