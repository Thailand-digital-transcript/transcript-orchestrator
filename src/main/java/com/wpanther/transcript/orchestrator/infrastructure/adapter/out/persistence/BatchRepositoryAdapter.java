package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.transcript.orchestrator.domain.model.Batch;
import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import com.wpanther.transcript.orchestrator.domain.repository.BatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BatchRepositoryAdapter implements BatchRepository {

    // Deterministic ordering for every paged/capped list read. Postgres has no
    // implicit row order, so without this the monitor's page N and page N+1 can
    // overlap or skip rows, and capped queue reads return an arbitrary 100.
    // newest-first by createdAt, tie-broken by id so the total order is stable.
    private static final Sort LIST_SORT =
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));

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
        return jpa.findByStatusIn(statuses, PageRequest.of(0, limit, LIST_SORT)).stream()
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
        return jpa.findByStatusInAndInstitutionCode(statuses, inst, PageRequest.of(0, limit, LIST_SORT))
                .stream()
                .map(BatchEntity::toDomain)
                .toList();
    }

    @Override
    public List<Batch> findByInstitutionCode(String inst, int page, int size) {
        return jpa.findByInstitutionCode(inst, PageRequest.of(page, size, LIST_SORT))
                .stream()
                .map(BatchEntity::toDomain)
                .toList();
    }

    @Override
    public long countByInstitutionCode(String inst) {
        return jpa.countByInstitutionCode(inst);
    }
}
