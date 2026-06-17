package com.wpanther.transcript.orchestrator.application;

import com.wpanther.transcript.orchestrator.application.usecase.CloseBatchUseCase;
import com.wpanther.transcript.orchestrator.domain.exception.*;
import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.domain.repository.*;
import com.wpanther.transcript.orchestrator.domain.service.BatchStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloseBatchUseCaseTest {
    @Mock BatchRepository batchRepository;
    @Mock TranscriptItemRepository itemRepository;
    @Mock BatchStateMachine stateMachine;
    @InjectMocks CloseBatchUseCase useCase;

    @Test void close_validDraftBatch_succeeds() {
        UUID batchId = UUID.randomUUID();
        Batch batch = Batch.create("Test", "KMUTT", "admin");
        TranscriptItem item = TranscriptItem.register("tx-1","doc-1","KMUTT","R","k.xml");
        item.assign(batchId);
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchIdAndStatusIn(eq(batchId), anyList())).thenReturn(List.of(item));
        when(batchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase.close(batchId, "admin");
        verify(stateMachine).close(eq(batch), eq(List.of(item)), eq("admin"));
    }
    @Test void close_batchNotFound_throws() {
        UUID batchId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.close(batchId, "admin"))
            .isInstanceOf(BatchNotFoundException.class);
    }
    @Test void close_noAssignedItems_throws() {
        UUID batchId = UUID.randomUUID();
        Batch batch = Batch.create("Test", "KMUTT", "admin");
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchIdAndStatusIn(any(), any())).thenReturn(List.of());
        doThrow(new EmptyBatchException()).when(stateMachine).close(any(), eq(List.of()), any());
        assertThatThrownBy(() -> useCase.close(batchId, "admin"))
            .isInstanceOf(EmptyBatchException.class);
    }
}
