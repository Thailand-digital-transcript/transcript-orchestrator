package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.transcript.orchestrator.domain.model.Batch;

import java.lang.reflect.Field;

/**
 * Restores aggregate fields on {@link Batch} from a {@link BatchEntity}.
 * <p>
 * Batch keeps state-transition mutators (e.g. {@code applyClose}) public, but
 * the "set-to-stored-value" setters (e.g. {@code setStatus}, {@code setVersion})
 * are package-private to {@code domain.model} — they exist only for the JPA
 * restore path. Since this mapper lives in a different package, it uses
 * reflection to write those fields, mirroring the pattern that other
 * transcript services use for the same reason.
 */
class BatchEntityMapper {

    static void restore(Batch batch, BatchEntity e) {
        // M2 fix: override the random UUID set by Batch.create() with the stored id.
        set(batch, "id", e.getId());
        set(batch, "version", e.getVersion());
        set(batch, "itemCount", e.getItemCount());
        set(batch, "status", e.getStatus());
        set(batch, "awaitingReplyFor", e.getAwaitingReplyFor());
        set(batch, "closedBy", e.getClosedBy());
        set(batch, "closedAt", e.getClosedAt());
        set(batch, "registrarApprovedBy", e.getRegistrarApprovedBy());
        set(batch, "registrarApprovedAt", e.getRegistrarApprovedAt());
        set(batch, "deanApprovedBy", e.getDeanApprovedBy());
        set(batch, "deanApprovedAt", e.getDeanApprovedAt());
        set(batch, "rejectedBy", e.getRejectedBy());
        set(batch, "rejectedAt", e.getRejectedAt());
        set(batch, "rejectionReason", e.getRejectionReason());
        set(batch, "failureReason", e.getFailureReason());
        set(batch, "createdAt", e.getCreatedAt());
        set(batch, "completedAt", e.getCompletedAt());
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot restore field " + field, ex);
        }
    }
}
