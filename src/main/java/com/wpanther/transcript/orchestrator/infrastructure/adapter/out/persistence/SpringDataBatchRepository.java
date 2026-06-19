package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.transcript.orchestrator.domain.model.BatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataBatchRepository extends JpaRepository<BatchEntity, UUID> {

    Optional<BatchEntity> findByAwaitingReplyFor(String correlationId);

    List<BatchEntity> findByStatusIn(List<BatchStatus> statuses, Pageable pageable);

    /**
     * Batches that entered an automatic phase (signing/sealing/PDF) and have not
     * progressed within the sweeper window. The M4 {@code updated_at} index makes
     * this query cheap.
     */
    @Query("""
        SELECT b FROM BatchEntity b
        WHERE b.status IN ('REGISTRAR_SIGNING','DEAN_SIGNING','SEALING','PDF_GENERATION','PDF_SIGNING')
          AND b.awaitingReplyFor IS NOT NULL
          AND b.updatedAt < :before
        """)
    List<BatchEntity> findAutomaticPhaseStuckBefore(Instant before, Pageable pageable);

    /**
     * Institution-scoped approval-queue read (A8). Backs
     * {@code BatchRepository#findByStatusInAndInstitutionCode}.
     */
    Page<BatchEntity> findByStatusInAndInstitutionCode(List<BatchStatus> statuses,
                                                      String institutionCode,
                                                      Pageable pageable);

    /**
     * Institution-scoped paginated monitor read (A8).
     */
    Page<BatchEntity> findByInstitutionCode(String institutionCode, Pageable pageable);

    /**
     * Institution-scoped batch count (A8). Drives the paginated monitor's
     * {@code totalElements}.
     */
    long countByInstitutionCode(String institutionCode);
}
