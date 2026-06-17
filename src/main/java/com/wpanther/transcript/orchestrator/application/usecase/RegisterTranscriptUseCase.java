package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.application.dto.event.InboundStartSagaCommand;
import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;
import com.wpanther.transcript.orchestrator.domain.repository.TranscriptItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j @Service @RequiredArgsConstructor
public class RegisterTranscriptUseCase {
    private final TranscriptItemRepository repository;

    @Transactional
    public void register(InboundStartSagaCommand cmd) {
        if (repository.findByTranscriptId(cmd.getTranscriptId()).isPresent()) {
            log.debug("Transcript {} already registered — skipping", cmd.getTranscriptId());
            return;
        }
        TranscriptItem item = TranscriptItem.register(cmd.getTranscriptId(), cmd.getDocumentId(),
            cmd.getInstitutionCode(), cmd.getTranscriptType(), cmd.getXmlStorageKey());
        repository.save(item);
        log.info("Registered transcript {} (doc: {})", cmd.getTranscriptId(), cmd.getDocumentId());
    }
}
