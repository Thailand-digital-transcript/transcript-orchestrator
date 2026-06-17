package com.wpanther.transcript.orchestrator.application.usecase;

import com.wpanther.transcript.orchestrator.application.dto.command.CreateBatchCommand;
import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.repository.BatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class CreateBatchUseCase {
    private final BatchRepository batchRepository;

    @Transactional
    public Batch create(CreateBatchCommand cmd) {
        return batchRepository.save(Batch.create(cmd.getName(), cmd.getInstitutionCode(), cmd.getCreatedBy()));
    }
}
