package com.wpanther.transcript.orchestrator.domain;

import com.wpanther.transcript.orchestrator.domain.model.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BatchStatusTest {
    @Test void batchStatusValues() {
        assertThat(BatchStatus.values()).containsExactlyInAnyOrder(
            BatchStatus.DRAFT, BatchStatus.PENDING_REGISTRAR, BatchStatus.REGISTRAR_SIGNING,
            BatchStatus.PENDING_DEAN, BatchStatus.DEAN_SIGNING, BatchStatus.SEALING,
            BatchStatus.PDF_GENERATION, BatchStatus.PDF_SIGNING, BatchStatus.COMPLETED,
            BatchStatus.CANCELLED, BatchStatus.FAILED);
    }
    @Test void itemStatusValues() {
        assertThat(ItemStatus.values()).containsExactlyInAnyOrder(
            ItemStatus.REGISTERED, ItemStatus.ASSIGNED, ItemStatus.REGISTRAR_SIGNED,
            ItemStatus.DEAN_SIGNED, ItemStatus.SEALED, ItemStatus.PDF_RENDERED,
            ItemStatus.PDF_SIGNED, ItemStatus.REJECTED, ItemStatus.FAILED);
    }
    @Test void predicates() {
        assertThat(BatchStatus.PENDING_REGISTRAR.isHumanGate()).isTrue();
        assertThat(BatchStatus.PENDING_DEAN.isHumanGate()).isTrue();
        assertThat(BatchStatus.REGISTRAR_SIGNING.isAutomatic()).isTrue();
        assertThat(BatchStatus.SEALING.isAutomatic()).isTrue();
        assertThat(BatchStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(BatchStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(BatchStatus.FAILED.isTerminal()).isTrue();
        assertThat(BatchStatus.DRAFT.isTerminal()).isFalse();
    }
}
