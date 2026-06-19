package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.domain.repository.BatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BatchRepositoryAdapter implements BatchRepository {

    private final SpringDataBatchRepository jpa;

    @Override
    public Batch save(Batch batch) {
        // saveAndFlush is required so the @Version increment is reflected
        // before the domain object is reused in the same TX
        // (see transcript-signing/CLAUDE.md "Optimistic lock / @Version" gotcha).
        return jpa.saveAndFlush(BatchEntity.fromDomain(batch)).toDomain();
    }

    @Override
    public Optional<Batch> findById(UUID id) {
        return jpa.findById(id).map(BatchEntity::toDomain);
    }

    @Override
    public Optional<Batch> findByAwaitingReplyFor(String correlationId) {
        return jpa.findByAwaitingReplyFor(correlationId).map(BatchEntity::toDomain);
    }

    @Override
    public List<Batch> findByStatusIn(List<BatchStatus> statuses, int limit) {
        return jpa.findByStatusIn(statuses, PageRequest.of(0, limit)).stream()
                .map(BatchEntity::toDomain)
                .toList();
    }

    @Override
    public List<Batch> findAutomaticPhaseStuckBefore(Instant before, int limit) {
        return jpa.findAutomaticPhaseStuckBefore(before, PageRequest.of(0, limit)).stream()
                .map(BatchEntity::toDomain)
                .toList();
    }

    @Override
    public List<Batch> findByStatusInAndInstitutionCode(List<BatchStatus> statuses,
                                                       String inst,
                                                       int limit) {
        return jpa.findByStatusInAndInstitutionCode(statuses, inst, PageRequest.of(0, limit))
                .stream()
                .map(BatchEntity::toDomain)
                .toList();
    }

    @Override
    public List<Batch> findByInstitutionCode(String inst, int page, int size) {
        return jpa.findByInstitutionCode(inst, PageRequest.of(page, size))
                .stream()
                .map(BatchEntity::toDomain)
                .toList();
    }

    @Override
    public long countByInstitutionCode(String inst) {
        return jpa.countByInstitutionCode(inst);
    }
}
