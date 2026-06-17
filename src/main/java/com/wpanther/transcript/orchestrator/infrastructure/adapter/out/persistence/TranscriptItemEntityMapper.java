package com.wpanther.transcript.orchestrator.infrastructure.adapter.out.persistence;

import com.wpanther.transcript.orchestrator.domain.model.TranscriptItem;

import java.lang.reflect.Field;

/**
 * Restores aggregate fields on {@link TranscriptItem} from a
 * {@link TranscriptItemEntity} via reflection — see {@link BatchEntityMapper}
 * for the rationale.
 */
class TranscriptItemEntityMapper {

    static void restore(TranscriptItem item, TranscriptItemEntity e) {
        set(item, "id", e.getId());
        set(item, "status", e.getStatus());
        set(item, "batchId", e.getBatchId());
        set(item, "registrarSignedXmlKey", e.getRegistrarSignedXmlKey());
        set(item, "deanSignedXmlKey", e.getDeanSignedXmlKey());
        set(item, "sealedXmlKey", e.getSealedXmlKey());
        set(item, "pdfKey", e.getPdfKey());
        set(item, "signedPdfKey", e.getSignedPdfKey());
        set(item, "rejectionReason", e.getRejectionReason());
        set(item, "failureReason", e.getFailureReason());
        set(item, "createdAt", e.getCreatedAt());
        set(item, "updatedAt", e.getUpdatedAt());
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
