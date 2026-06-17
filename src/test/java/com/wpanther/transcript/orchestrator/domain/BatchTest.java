package com.wpanther.transcript.orchestrator.domain;

import com.wpanther.transcript.orchestrator.domain.model.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class BatchTest {
    @Test void create_setsInitialState() {
        Batch b = Batch.create("Graduation-2025", "KMUTT", "admin");
        assertThat(b.getName()).isEqualTo("Graduation-2025");
        assertThat(b.getStatus()).isEqualTo(BatchStatus.DRAFT);
        assertThat(b.getItemCount()).isZero();
        assertThat(b.getAwaitingReplyFor()).isNull();
        assertThat(b.getId()).isNotNull();
    }
    @Test void applyClose_transitionsToPendingRegistrar() {
        Batch b = Batch.create("Test", "KMUTT", "user");
        b.applyClose("user", Instant.now());
        assertThat(b.getStatus()).isEqualTo(BatchStatus.PENDING_REGISTRAR);
    }
    @Test void applySigningStarted_setsCorrelationId() {
        Batch b = Batch.create("Test", "KMUTT", "user");
        b.applyClose("user", Instant.now());
        b.applyRegistrarApproved("r", Instant.now());
        b.applySigningStarted("corr-1");
        assertThat(b.getAwaitingReplyFor()).isEqualTo("corr-1");
    }
    @Test void applyPdfGenerationStarted_setsCorrelationId() {
        Batch b = Batch.create("Test", "KMUTT", "user");
        b.applyPdfGenerationStarted("corr-pdf");
        assertThat(b.getAwaitingReplyFor()).isEqualTo("corr-pdf");
    }
    @Test void applyCancelled_setsFields() {
        Batch b = Batch.create("Test", "KMUTT", "user");
        b.applyClose("user", Instant.now());
        b.applyCancelled("reg", Instant.now(), "Not ready");
        assertThat(b.getStatus()).isEqualTo(BatchStatus.CANCELLED);
        assertThat(b.getRejectedBy()).isEqualTo("reg");
    }
    @Test void applyFailed_setsFailed() {
        Batch b = Batch.create("Test", "KMUTT", "user");
        b.applyFailed("No healthy items");
        assertThat(b.getStatus()).isEqualTo(BatchStatus.FAILED);
    }
    @Test void fullPath_toCompleted() {
        Batch b = Batch.create("Test", "KMUTT", "user");
        b.applyClose("user", Instant.now());
        b.applyRegistrarApproved("r", Instant.now());
        b.applySigningStarted("c1");
        b.applyRegistrarSigningComplete();
        assertThat(b.getStatus()).isEqualTo(BatchStatus.PENDING_DEAN);
        assertThat(b.getAwaitingReplyFor()).isNull();
        b.applyDeanApproved("d", Instant.now());
        b.applySigningStarted("c2");
        b.applyDeanSigningComplete();
        assertThat(b.getStatus()).isEqualTo(BatchStatus.SEALING);
        b.applySigningStarted("c3");
        b.applySealingComplete();
        assertThat(b.getStatus()).isEqualTo(BatchStatus.PDF_GENERATION);
        b.applyPdfGenerationStarted("c4");
        b.applyPdfGenerationComplete();
        assertThat(b.getStatus()).isEqualTo(BatchStatus.PDF_SIGNING);
        b.applySigningStarted("c5");
        b.applyPdfSigningComplete();
        assertThat(b.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(b.getCompletedAt()).isNotNull();
    }
}
