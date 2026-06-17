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
        // I1 fix: required fields throw on null (catches data-migration / bad-row
        // corruption); optional fields are skipped when null so the domain object
        // retains its create()-time defaults.
        setRequired(batch, "id", e.getId(), "BatchEntity.id");
        setRequired(batch, "version", e.getVersion(), "BatchEntity.version");
        setRequired(batch, "itemCount", e.getItemCount(), "BatchEntity.itemCount");
        setRequired(batch, "status", e.getStatus(), "BatchEntity.status");
        setRequired(batch, "createdAt", e.getCreatedAt(), "BatchEntity.createdAt");

        setIfNotNull(batch, "awaitingReplyFor", e.getAwaitingReplyFor());
        setIfNotNull(batch, "closedBy", e.getClosedBy());
        setIfNotNull(batch, "closedAt", e.getClosedAt());
        setIfNotNull(batch, "registrarApprovedBy", e.getRegistrarApprovedBy());
        setIfNotNull(batch, "registrarApprovedAt", e.getRegistrarApprovedAt());
        setIfNotNull(batch, "deanApprovedBy", e.getDeanApprovedBy());
        setIfNotNull(batch, "deanApprovedAt", e.getDeanApprovedAt());
        setIfNotNull(batch, "rejectedBy", e.getRejectedBy());
        setIfNotNull(batch, "rejectedAt", e.getRejectedAt());
        setIfNotNull(batch, "rejectionReason", e.getRejectionReason());
        setIfNotNull(batch, "failureReason", e.getFailureReason());
        setIfNotNull(batch, "completedAt", e.getCompletedAt());
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

    private static void setRequired(Object target, String field, Object value, String columnLabel) {
        if (value == null) {
            throw new IllegalStateException(columnLabel + " is null but required for Batch restore");
        }
        set(target, field, value);
    }

    private static void setIfNotNull(Object target, String field, Object value) {
        if (value != null) set(target, field, value);
    }
}
