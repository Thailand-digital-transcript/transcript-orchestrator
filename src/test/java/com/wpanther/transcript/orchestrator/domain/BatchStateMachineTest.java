package com.wpanther.transcript.orchestrator.domain;

import com.wpanther.transcript.orchestrator.domain.exception.*;
import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.domain.service.BatchStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BatchStateMachineTest {

    BatchStateMachine machine;
    Batch batch;

    @BeforeEach void setUp() {
        machine = new BatchStateMachine();
        batch = Batch.create("Test Batch", "KMUTT", "admin");
    }

    // --- close ---
    @Test void close_fromDraft_succeeds() {
        machine.close(batch, List.of(anItem()), "admin");
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PENDING_REGISTRAR);
    }
    @Test void close_emptyItems_throws() {
        assertThatThrownBy(() -> machine.close(batch, List.of(), "admin"))
            .isInstanceOf(EmptyBatchException.class);
    }
    @Test void close_notDraft_throws() {
        machine.close(batch, List.of(anItem()), "admin");
        assertThatThrownBy(() -> machine.close(batch, List.of(anItem()), "admin"))
            .isInstanceOf(InvalidBatchStateException.class);
    }

    // --- registrar approval ---
    @Test void registrarApprove_fromPendingRegistrar_transitionsToRegistrarSigning() {
        machine.close(batch, List.of(anItem()), "admin");
        machine.registrarApprove(batch, "KMUTT", "reg01", Instant.now());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.REGISTRAR_SIGNING);
    }
    @Test void registrarApprove_institutionMismatch_throws() {
        machine.close(batch, List.of(anItem()), "admin");
        assertThatThrownBy(() -> machine.registrarApprove(batch, "OTHER", "r", Instant.now()))
            .isInstanceOf(InstitutionMismatchException.class);
    }
    @Test void registrarApprove_wrongState_throws() {
        assertThatThrownBy(() -> machine.registrarApprove(batch, "KMUTT", "r", Instant.now()))
            .isInstanceOf(InvalidBatchStateException.class);
    }
    @Test void applyItemRejections_subsetRejected_batchContinues() {
        TranscriptItem i1 = anItem("doc-1"); TranscriptItem i2 = anItem("doc-2");
        List<TranscriptItem> items = new ArrayList<>(List.of(i1, i2));
        machine.close(batch, items, "admin");
        machine.registrarApprove(batch, "KMUTT", "r", Instant.now());
        boolean cancelled = machine.applyItemRejections(batch, items,
            List.of(i1.getDocumentId()), "r", Instant.now(), "Rejected by registrar");
        assertThat(cancelled).isFalse();
        assertThat(i1.getStatus()).isEqualTo(ItemStatus.REJECTED);
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.REGISTRAR_SIGNING);
    }
    @Test void applyItemRejections_allRejected_cancelsBatch() {
        TranscriptItem i1 = anItem("doc-1");
        List<TranscriptItem> items = new ArrayList<>(List.of(i1));
        machine.close(batch, items, "admin");
        machine.registrarApprove(batch, "KMUTT", "r", Instant.now());
        boolean cancelled = machine.applyItemRejections(batch, items,
            List.of(i1.getDocumentId()), "r", Instant.now(), "Rejected by registrar");
        assertThat(cancelled).isTrue();
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
    }
    @Test void registrarReject_cancelsBatch() {
        machine.close(batch, List.of(anItem()), "admin");
        machine.registrarReject(batch, "KMUTT", "r", Instant.now(), "Not ready");
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
    }

    // --- signing started ---
    @Test void signingStarted_setsCorrelationId() {
        machine.close(batch, List.of(anItem()), "admin");
        machine.registrarApprove(batch, "KMUTT", "r", Instant.now());
        machine.signingStarted(batch, "corr-1");
        assertThat(batch.getAwaitingReplyFor()).isEqualTo("corr-1");
    }

    // --- signing reply ---
    @Test void signingReply_fromRegistrarSigning_transitionsToPendingDean() {
        List<TranscriptItem> items = new ArrayList<>(List.of(anItem("doc-1")));
        machine.close(batch, items, "admin");
        machine.registrarApprove(batch, "KMUTT", "r", Instant.now());
        machine.signingStarted(batch, "corr-1");
        machine.signingReply(batch, items, "corr-1",
            Map.of("doc-1", "signed/doc-1.xml"), Map.of());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PENDING_DEAN);
        assertThat(items.get(0).getStatus()).isEqualTo(ItemStatus.REGISTRAR_SIGNED);
        assertThat(batch.getAwaitingReplyFor()).isNull();
    }
    @Test void signingReply_wrongCorrelationId_isIgnored() {
        List<TranscriptItem> items = new ArrayList<>(List.of(anItem("doc-1")));
        machine.close(batch, items, "admin");
        machine.registrarApprove(batch, "KMUTT", "r", Instant.now());
        machine.signingStarted(batch, "corr-1");
        machine.signingReply(batch, items, "WRONG", Map.of(), Map.of());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.REGISTRAR_SIGNING);
    }
    @Test void signingReply_replayed_afterStateAdvanced_isNoOp() {
        // After the reply is processed, awaitingReplyFor=null; replaying must be ignored.
        List<TranscriptItem> items = new ArrayList<>(List.of(anItem("doc-1")));
        machine.close(batch, items, "admin");
        machine.registrarApprove(batch, "KMUTT", "r", Instant.now());
        machine.signingStarted(batch, "corr-1");
        machine.signingReply(batch, items, "corr-1", Map.of("doc-1", "signed.xml"), Map.of());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PENDING_DEAN);
        // Replay: awaitingReplyFor is now null — the correlationId guard must fire and return
        machine.signingReply(batch, items, "corr-1", Map.of("doc-1", "signed2.xml"), Map.of());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PENDING_DEAN);
    }
    // M3 Rev 1 fix: replay guard also leaves item state untouched.
    @Test void signingReply_replayAfterAdvance_isNoop() {
        List<TranscriptItem> items = new ArrayList<>(List.of(anItem("doc-1")));
        machine.close(batch, items, "admin");
        machine.registrarApprove(batch, "KMUTT", "r", Instant.now());
        machine.signingStarted(batch, "corr-1");
        machine.signingReply(batch, items, "corr-1",
            Map.of("doc-1", "signed/doc-1.xml"), Map.of());
        // Capture item state after the legitimate first reply (batch is now PENDING_DEAN).
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PENDING_DEAN);
        assertThat(batch.getAwaitingReplyFor()).isNull();
        ItemStatus statusAfterFirst = items.get(0).getStatus();
        String signedKeyAfterFirst = items.get(0).getRegistrarSignedXmlKey();
        // Replay with the same correlationId — the correlationId guard must fire and return.
        machine.signingReply(batch, items, "corr-1",
            Map.of("doc-1", "different-key.xml"), Map.of());
        // Batch still PENDING_DEAN; items must be unchanged.
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PENDING_DEAN);
        assertThat(items.get(0).getStatus()).isEqualTo(statusAfterFirst);
        assertThat(items.get(0).getRegistrarSignedXmlKey()).isEqualTo(signedKeyAfterFirst);
    }
    @Test void signingReply_allItemsFailed_batchFails() {
        List<TranscriptItem> items = new ArrayList<>(List.of(anItem("doc-1")));
        machine.close(batch, items, "admin");
        machine.registrarApprove(batch, "KMUTT", "r", Instant.now());
        machine.signingStarted(batch, "corr-1");
        machine.signingReply(batch, items, "corr-1", Map.of(), Map.of("doc-1", "CSC error"));
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    // --- dean approval ---
    @Test void deanApprove_fromPendingDean_transitionsToDeanSigning() {
        List<TranscriptItem> items = new ArrayList<>(List.of(anItem("doc-1")));
        runToRegistrarSigningComplete(items);
        machine.deanApprove(batch, "KMUTT", "dean01", Instant.now());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.DEAN_SIGNING);
    }
    @Test void deanApprove_institutionMismatch_throws() {
        List<TranscriptItem> items = new ArrayList<>(List.of(anItem("doc-1")));
        runToRegistrarSigningComplete(items);
        assertThatThrownBy(() -> machine.deanApprove(batch, "OTHER", "d", Instant.now()))
            .isInstanceOf(InstitutionMismatchException.class);
    }
    @Test void deanApprove_alreadyAdvanced_isIdempotentNoop() {
        List<TranscriptItem> items = new ArrayList<>(List.of(anItem("doc-1")));
        runToRegistrarSigningComplete(items);
        machine.deanApprove(batch, "KMUTT", "d", Instant.now());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.DEAN_SIGNING);
        // Second approval while batch is DEAN_SIGNING — must not throw, must not change state
        assertThatCode(() -> machine.deanApprove(batch, "KMUTT", "d2", Instant.now()))
            .doesNotThrowAnyException();
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.DEAN_SIGNING);
    }
    @Test void deanReject_cancelsBatch() {
        List<TranscriptItem> items = new ArrayList<>(List.of(anItem("doc-1")));
        runToRegistrarSigningComplete(items);
        machine.deanReject(batch, "KMUTT", "d", Instant.now(), "Rejected by dean");
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.CANCELLED);
    }

    // --- full automatic path ---
    @Test void fullPath_deanSigning_to_completed() {
        List<TranscriptItem> items = new ArrayList<>(List.of(anItem("doc-1")));
        runToRegistrarSigningComplete(items);
        machine.deanApprove(batch, "KMUTT", "dean", Instant.now());
        machine.signingStarted(batch, "corr-2");
        machine.signingReply(batch, items, "corr-2", Map.of("doc-1", "dean-signed.xml"), Map.of());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.SEALING);
        machine.signingStarted(batch, "corr-3");
        machine.signingReply(batch, items, "corr-3", Map.of("doc-1", "sealed.xml"), Map.of());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PDF_GENERATION);
        machine.pdfGenerationStarted(batch, "corr-4");
        machine.pdfGenerationReply(batch, items, "corr-4", Map.of("doc-1", "doc.pdf"), Map.of());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PDF_SIGNING);
        machine.signingStarted(batch, "corr-5");
        machine.signingReply(batch, items, "corr-5", Map.of("doc-1", "signed.pdf"), Map.of());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(batch.getCompletedAt()).isNotNull();
        assertThat(items.get(0).getStatus()).isEqualTo(ItemStatus.PDF_SIGNED);
    }

    // --- partial failure ---
    @Test void signingReply_someItemsFailed_batchContinuesWithHealthy() {
        TranscriptItem i1 = anItem("doc-1"); TranscriptItem i2 = anItem("doc-2");
        List<TranscriptItem> items = new ArrayList<>(List.of(i1, i2));
        machine.close(batch, items, "admin");
        machine.registrarApprove(batch, "KMUTT", "r", Instant.now());
        machine.signingStarted(batch, "corr-1");
        machine.signingReply(batch, items, "corr-1",
            Map.of("doc-1", "signed.xml"),
            Map.of("doc-2", "CSC error"));
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PENDING_DEAN);
        assertThat(i1.getStatus()).isEqualTo(ItemStatus.REGISTRAR_SIGNED);
        assertThat(i2.getStatus()).isEqualTo(ItemStatus.FAILED);
    }

    // --- pdf reply ---
    @Test void pdfGenerationReply_wrongCorrelationId_isIgnored() {
        List<TranscriptItem> items = new ArrayList<>(List.of(anItem("doc-1")));
        runToPdfGeneration(items);
        machine.pdfGenerationStarted(batch, "corr-pdf");
        machine.pdfGenerationReply(batch, items, "WRONG", Map.of(), Map.of());
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.PDF_GENERATION);
    }

    // --- helpers ---
    private TranscriptItem anItem() { return anItem("doc-" + UUID.randomUUID()); }
    private TranscriptItem anItem(String documentId) {
        return TranscriptItem.register("tx-" + documentId, documentId, "KMUTT", "REGULAR",
            documentId + ".xml");
    }
    private void runToRegistrarSigningComplete(List<TranscriptItem> items) {
        machine.close(batch, items, "admin");
        machine.registrarApprove(batch, "KMUTT", "r", Instant.now());
        machine.signingStarted(batch, "corr-r");
        Map<String, String> results = new HashMap<>();
        items.forEach(i -> results.put(i.getDocumentId(), "reg-" + i.getDocumentId() + ".xml"));
        machine.signingReply(batch, items, "corr-r", results, Map.of());
    }
    private void runToPdfGeneration(List<TranscriptItem> items) {
        runToRegistrarSigningComplete(items);
        machine.deanApprove(batch, "KMUTT", "dean", Instant.now());
        machine.signingStarted(batch, "corr-d");
        Map<String, String> dr = new HashMap<>();
        items.forEach(i -> dr.put(i.getDocumentId(), "dean-" + i.getDocumentId() + ".xml"));
        machine.signingReply(batch, items, "corr-d", dr, Map.of());
        machine.signingStarted(batch, "corr-s");
        Map<String, String> sr = new HashMap<>();
        items.forEach(i -> sr.put(i.getDocumentId(), "seal-" + i.getDocumentId() + ".xml"));
        machine.signingReply(batch, items, "corr-s", sr, Map.of());
    }
}
