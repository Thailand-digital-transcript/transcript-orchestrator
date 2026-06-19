package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.application.dto.command.SubmitDecisionCommand;
import com.wpanther.transcript.orchestrator.application.port.out.ApprovalCommandPort;
import com.wpanther.transcript.orchestrator.domain.exception.*;
import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SubmitBatchDecisionUseCaseTest {
    BatchRepository batches = mock(BatchRepository.class);
    TranscriptItemRepository items = mock(TranscriptItemRepository.class);
    ApprovalCommandPort port = mock(ApprovalCommandPort.class);
    PendingDecisionRepository decisions = mock(PendingDecisionRepository.class);
    SubmitBatchDecisionUseCase uc;

    UUID batchId = UUID.randomUUID();

    @BeforeEach void setUp() {
        uc = new SubmitBatchDecisionUseCase(batches, items, port, decisions);
        when(port.publish(any(), any(), any())).thenReturn(UUID.randomUUID());
        when(decisions.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Batch batchAt(BatchStatus status, String inst) {
        Batch b = mock(Batch.class);
        when(b.getId()).thenReturn(batchId);
        when(b.getStatus()).thenReturn(status);
        when(b.getInstitutionCode()).thenReturn(inst);
        return b;
    }

    @Test void approveAtRegistrarGatePublishesAndRecords() {
        Batch batch = batchAt(BatchStatus.PENDING_REGISTRAR, "01110");
        when(batches.findById(batchId)).thenReturn(Optional.of(batch));
        var cmd = new SubmitDecisionCommand(batchId, BatchStatus.PENDING_REGISTRAR,
                "alice", "01110", "APPROVE", List.of(), null);
        UUID id = uc.submit(cmd);
        assertThat(id).isNotNull();
        verify(port).publish(eq(BatchStatus.PENDING_REGISTRAR), any(), eq(batchId.toString()));
        verify(decisions).save(argThat(d -> d.outboxEventId() != null));
    }

    @Test void wrongGateThrows409() {
        Batch batch = batchAt(BatchStatus.PENDING_DEAN, "01110");
        when(batches.findById(batchId)).thenReturn(Optional.of(batch));
        var cmd = new SubmitDecisionCommand(batchId, BatchStatus.PENDING_REGISTRAR,
                "alice", "01110", "APPROVE", List.of(), null);
        assertThatThrownBy(() -> uc.submit(cmd)).isInstanceOf(InvalidBatchStateException.class);
    }

    @Test void crossInstitutionThrowsMismatch() {
        Batch batch = batchAt(BatchStatus.PENDING_REGISTRAR, "99999");
        when(batches.findById(batchId)).thenReturn(Optional.of(batch));
        var cmd = new SubmitDecisionCommand(batchId, BatchStatus.PENDING_REGISTRAR,
                "alice", "01110", "APPROVE", List.of(), null);
        assertThatThrownBy(() -> uc.submit(cmd)).isInstanceOf(InstitutionMismatchException.class);
    }

    @Test void rejectWithoutReasonThrows400() {
        Batch batch = batchAt(BatchStatus.PENDING_REGISTRAR, "01110");
        when(batches.findById(batchId)).thenReturn(Optional.of(batch));
        var cmd = new SubmitDecisionCommand(batchId, BatchStatus.PENDING_REGISTRAR,
                "alice", "01110", "REJECT", List.of(), "  ");
        assertThatThrownBy(() -> uc.submit(cmd)).isInstanceOf(DecisionValidationException.class);
    }

    @Test void unknownRejectedDocIdThrows400() {
        Batch batch = batchAt(BatchStatus.PENDING_REGISTRAR, "01110");
        when(batches.findById(batchId)).thenReturn(Optional.of(batch));
        when(items.findByBatchId(batchId)).thenReturn(List.of());
        var cmd = new SubmitDecisionCommand(batchId, BatchStatus.PENDING_REGISTRAR,
                "alice", "01110", "APPROVE", List.of("doc-unknown"), null);
        assertThatThrownBy(() -> uc.submit(cmd)).isInstanceOf(DecisionValidationException.class);
    }
}
