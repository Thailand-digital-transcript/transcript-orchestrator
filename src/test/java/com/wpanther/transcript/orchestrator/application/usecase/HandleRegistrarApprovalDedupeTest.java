package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.application.dto.event.RegistrarApprovalEvent;
import com.wpanther.transcript.orchestrator.application.port.out.BatchSigningCommandPort;
import com.wpanther.transcript.orchestrator.domain.repository.*;
import com.wpanther.transcript.orchestrator.domain.service.BatchStateMachine;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;

class HandleRegistrarApprovalDedupeTest {
    BatchRepository batches = mock(BatchRepository.class);
    TranscriptItemRepository items = mock(TranscriptItemRepository.class);
    BatchStateMachine sm = mock(BatchStateMachine.class);
    BatchSigningCommandPort signing = mock(BatchSigningCommandPort.class);
    PendingDecisionRepository decisions = mock(PendingDecisionRepository.class);

    @Test
    void alreadyClaimedDecisionIsSkipped() {
        var uc = new HandleRegistrarApprovalUseCase(batches, items, sm, signing, decisions);
        UUID decisionId = UUID.randomUUID();
        when(decisions.claim(decisionId)).thenReturn(false); // already processed

        uc.handle(new RegistrarApprovalEvent(decisionId.toString(), UUID.randomUUID().toString(),
                "APPROVE", "01110", "alice", Instant.now(), List.of(), null));

        verifyNoInteractions(batches, sm, signing); // no state change
    }
}
