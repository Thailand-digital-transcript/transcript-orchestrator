package com.wpanther.transcript.orchestrator.application;

import com.wpanther.transcript.orchestrator.application.dto.event.InboundStartSagaCommand;
import com.wpanther.transcript.orchestrator.application.usecase.RegisterTranscriptUseCase;
import com.wpanther.transcript.orchestrator.domain.model.ItemStatus;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterTranscriptUseCaseTest {
    @Mock TranscriptItemRepository repository;
    @InjectMocks RegisterTranscriptUseCase useCase;

    @Test void register_newTranscript_createsRegisteredItem() {
        when(repository.findByTranscriptId("tx-1")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase.register(new InboundStartSagaCommand("tx-1","doc-001","KMUTT","REGULAR","xmls/doc.xml"));
        ArgumentCaptor<TranscriptItem> c = ArgumentCaptor.forClass(TranscriptItem.class);
        verify(repository).save(c.capture());
        assertThat(c.getValue().getStatus()).isEqualTo(ItemStatus.REGISTERED);
        assertThat(c.getValue().getOriginalXmlStorageKey()).isEqualTo("xmls/doc.xml");
    }
    @Test void register_duplicate_isIdempotentNoop() {
        when(repository.findByTranscriptId("tx-1")).thenReturn(
            Optional.of(TranscriptItem.register("tx-1","d","KMUTT","R","k")));
        useCase.register(new InboundStartSagaCommand("tx-1","doc-001","KMUTT","REGULAR","xmls/doc.xml"));
        verify(repository, never()).save(any());
    }
    @Test void register_nullXmlStorageKey_registersItemWithNullKey() {
        when(repository.findByTranscriptId("tx-2")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase.register(new InboundStartSagaCommand("tx-2","doc-002","KMUTT","REGULAR",null));
        ArgumentCaptor<TranscriptItem> c = ArgumentCaptor.forClass(TranscriptItem.class);
        verify(repository).save(c.capture());
        assertThat(c.getValue().getOriginalXmlStorageKey()).isNull();
    }
}
