package com.wpanther.transcript.orchestrator.domain.service;

import com.wpanther.transcript.orchestrator.domain.exception.*;
import com.wpanther.transcript.orchestrator.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.*;

public class BatchStateMachine {

    private static final Logger log = LoggerFactory.getLogger(BatchStateMachine.class);

    /** DRAFT → PENDING_REGISTRAR. Items must be non-empty. */
    public void close(Batch batch, List<TranscriptItem> items, String closedBy) {
        if (batch.getStatus() != BatchStatus.DRAFT)
            throw new InvalidBatchStateException("Cannot close batch in state " + batch.getStatus());
        if (items == null || items.isEmpty()) throw new EmptyBatchException();
        batch.applyClose(closedBy, Instant.now());
    }

    /** PENDING_REGISTRAR → REGISTRAR_SIGNING. Item rejections applied separately. */
    public void registrarApprove(Batch batch, String institutionCode,
            String approvedBy, Instant approvedAt) {
        requireState(batch, BatchStatus.PENDING_REGISTRAR);
        requireInstitution(batch, institutionCode);
        batch.applyRegistrarApproved(approvedBy, approvedAt);
    }

    /**
     * Marks listed documentIds REJECTED.
     * If all remaining items are terminal (REJECTED or FAILED), cancels the batch and returns true.
     */
    public boolean applyItemRejections(Batch batch, List<TranscriptItem> items,
            List<String> rejectedDocumentIds, String rejectedBy, Instant rejectedAt,
            String rejectionLabel) {
        rejectedDocumentIds.forEach(docId ->
            items.stream().filter(i -> docId.equals(i.getDocumentId())).findFirst()
                .ifPresent(i -> i.reject(rejectionLabel)));
        boolean allTerminal = items.stream().allMatch(TranscriptItem::isTerminal);
        if (allTerminal) batch.applyCancelled(rejectedBy, rejectedAt, "All items " + rejectionLabel);
        return allTerminal;
    }

    /** PENDING_REGISTRAR → CANCELLED (whole-batch registrar reject). */
    public void registrarReject(Batch batch, String institutionCode,
            String rejectedBy, Instant rejectedAt, String reason) {
        requireState(batch, BatchStatus.PENDING_REGISTRAR);
        requireInstitution(batch, institutionCode);
        batch.applyCancelled(rejectedBy, rejectedAt, reason);
    }

    /**
     * PENDING_DEAN → DEAN_SIGNING (idempotent: no-op if already advanced).
     * Idempotent because dean approval events can be redelivered after the batch
     * has already advanced. Registrar path is NOT idempotent — a duplicate registrar
     * approval while batch is REGISTRAR_SIGNING indicates a bug.
     */
    public void deanApprove(Batch batch, String institutionCode,
            String approvedBy, Instant approvedAt) {
        requireInstitution(batch, institutionCode);
        if (batch.getStatus() != BatchStatus.PENDING_DEAN) {
            log.debug("Ignoring dean approval for batch {} in state {}", batch.getId(), batch.getStatus());
            return;
        }
        batch.applyDeanApproved(approvedBy, approvedAt);
    }

    /** PENDING_DEAN → CANCELLED (whole-batch dean reject). */
    public void deanReject(Batch batch, String institutionCode,
            String rejectedBy, Instant rejectedAt, String reason) {
        requireState(batch, BatchStatus.PENDING_DEAN);
        requireInstitution(batch, institutionCode);
        batch.applyCancelled(rejectedBy, rejectedAt, reason);
    }

    public void signingStarted(Batch batch, String correlationId) {
        batch.applySigningStarted(correlationId);
    }

    public void pdfGenerationStarted(Batch batch, String correlationId) {
        batch.applyPdfGenerationStarted(correlationId);
    }

    /**
     * Joins a signing reply. No-op if correlationId doesn't match awaitingReplyFor.
     * Advances state based on current phase.
     *
     * @param successByDocId documentId → signed storage key
     * @param errorByDocId   documentId → error message
     */
    public void signingReply(Batch batch, List<TranscriptItem> items,
            String correlationId, Map<String, String> successByDocId,
            Map<String, String> errorByDocId) {
        if (!correlationId.equals(batch.getAwaitingReplyFor())) {
            log.debug("Ignoring signing reply correlationId={} — batch {} awaiting={}",
                correlationId, batch.getId(), batch.getAwaitingReplyFor());
            return;
        }
        applyItemResults(items, successByDocId, errorByDocId, batch.getStatus());
        long healthy = items.stream().filter(TranscriptItem::isHealthy).count();
        if (healthy == 0) {
            batch.applyFailed("No healthy items after " + batch.getStatus());
            return;
        }
        switch (batch.getStatus()) {
            case REGISTRAR_SIGNING -> batch.applyRegistrarSigningComplete();
            case DEAN_SIGNING      -> batch.applyDeanSigningComplete();
            case SEALING           -> batch.applySealingComplete();
            case PDF_SIGNING       -> batch.applyPdfSigningComplete();
            default -> throw new InvalidBatchStateException(
                "Unexpected signing reply for batch in state " + batch.getStatus());
        }
    }

    /**
     * Joins a PDF generation reply. No-op if correlationId doesn't match.
     * PDF_GENERATION → PDF_SIGNING.
     */
    public void pdfGenerationReply(Batch batch, List<TranscriptItem> items,
            String correlationId, Map<String, String> successByDocId,
            Map<String, String> errorByDocId) {
        if (!correlationId.equals(batch.getAwaitingReplyFor())) {
            log.debug("Ignoring PDF reply correlationId={} — batch {} awaiting={}",
                correlationId, batch.getId(), batch.getAwaitingReplyFor());
            return;
        }
        if (batch.getStatus() != BatchStatus.PDF_GENERATION) return;
        for (TranscriptItem item : items) {
            if (successByDocId.containsKey(item.getDocumentId()))
                item.markPdfRendered(successByDocId.get(item.getDocumentId()));
            else if (errorByDocId.containsKey(item.getDocumentId()))
                item.fail(errorByDocId.get(item.getDocumentId()));
        }
        long healthy = items.stream().filter(TranscriptItem::isHealthy).count();
        if (healthy == 0) batch.applyFailed("All items failed PDF generation");
        else              batch.applyPdfGenerationComplete();
    }

    // --- helpers ---
    private void applyItemResults(List<TranscriptItem> items,
            Map<String, String> successByDocId, Map<String, String> errorByDocId,
            BatchStatus phase) {
        for (TranscriptItem item : items) {
            if (item.isTerminal()) continue;
            String key = successByDocId.get(item.getDocumentId());
            String err = errorByDocId.get(item.getDocumentId());
            if (key != null) {
                switch (phase) {
                    case REGISTRAR_SIGNING -> item.markRegistrarSigned(key);
                    case DEAN_SIGNING      -> item.markDeanSigned(key);
                    case SEALING           -> item.markSealed(key);
                    case PDF_SIGNING       -> item.markPdfSigned(key);
                    default -> {}
                }
            } else if (err != null) {
                item.fail(err);
            }
        }
    }
    private void requireState(Batch batch, BatchStatus expected) {
        if (batch.getStatus() != expected)
            throw new InvalidBatchStateException(
                "Expected " + expected + " but batch " + batch.getId() + " is " + batch.getStatus());
    }
    private void requireInstitution(Batch batch, String code) {
        if (!batch.getInstitutionCode().equals(code))
            throw new InstitutionMismatchException(batch.getInstitutionCode(), code);
    }
}
