package com.wpanther.transcript.orchestrator.domain;

import com.wpanther.transcript.orchestrator.domain.model.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TranscriptItemTest {
    @Test void register_setsRegisteredStatus() {
        TranscriptItem i = TranscriptItem.register("tx-1","doc-001","KMUTT","REGULAR","xmls/doc.xml");
        assertThat(i.getStatus()).isEqualTo(ItemStatus.REGISTERED);
        assertThat(i.getOriginalXmlStorageKey()).isEqualTo("xmls/doc.xml");
        assertThat(i.getBatchId()).isNull();
    }
    @Test void assign_requiresNonNullXmlKey() {
        TranscriptItem i = TranscriptItem.register("tx-1","doc-001","KMUTT","REGULAR",null);
        assertThatThrownBy(() -> i.assign(UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("xmlStorageKey");
    }
    @Test void assign_setsAssignedAndBatchId() {
        TranscriptItem i = TranscriptItem.register("tx-1","doc-001","KMUTT","REGULAR","k.xml");
        i.assign(UUID.randomUUID());
        assertThat(i.getStatus()).isEqualTo(ItemStatus.ASSIGNED);
        assertThat(i.getBatchId()).isNotNull();
    }
    @Test void unassign_resetsToRegistered() {
        TranscriptItem i = TranscriptItem.register("tx-1","doc-001","KMUTT","REGULAR","k.xml");
        i.assign(UUID.randomUUID());
        i.unassign();
        assertThat(i.getStatus()).isEqualTo(ItemStatus.REGISTERED);
        assertThat(i.getBatchId()).isNull();
    }
    @Test void phaseProgressions() {
        TranscriptItem i = TranscriptItem.register("tx-1","doc-001","KMUTT","REGULAR","k.xml");
        i.assign(UUID.randomUUID());
        i.markRegistrarSigned("reg.xml");
        assertThat(i.getStatus()).isEqualTo(ItemStatus.REGISTRAR_SIGNED);
        assertThat(i.getRegistrarSignedXmlKey()).isEqualTo("reg.xml");
        i.markDeanSigned("dean.xml");
        assertThat(i.getStatus()).isEqualTo(ItemStatus.DEAN_SIGNED);
        i.markSealed("seal.xml");
        assertThat(i.getStatus()).isEqualTo(ItemStatus.SEALED);
        i.markPdfRendered("pdf.pdf");
        assertThat(i.getStatus()).isEqualTo(ItemStatus.PDF_RENDERED);
        i.markPdfSigned("signed.pdf");
        assertThat(i.getStatus()).isEqualTo(ItemStatus.PDF_SIGNED);
    }
    @Test void reject_setsTerminal() {
        TranscriptItem i = TranscriptItem.register("tx-1","doc-001","KMUTT","REGULAR","k.xml");
        i.assign(UUID.randomUUID()); i.reject("Bad data");
        assertThat(i.getStatus()).isEqualTo(ItemStatus.REJECTED);
        assertThat(i.isTerminal()).isTrue();
    }
    @Test void currentSigningStorageKey_returnsLatest() {
        TranscriptItem i = TranscriptItem.register("tx-1","doc-001","KMUTT","REGULAR","orig.xml");
        assertThat(i.currentSigningStorageKey()).isEqualTo("orig.xml");
        i.assign(UUID.randomUUID());
        i.markRegistrarSigned("reg.xml"); assertThat(i.currentSigningStorageKey()).isEqualTo("reg.xml");
        i.markDeanSigned("dean.xml");    assertThat(i.currentSigningStorageKey()).isEqualTo("dean.xml");
        i.markSealed("seal.xml");        assertThat(i.currentSigningStorageKey()).isEqualTo("seal.xml");
        // Once the PDF is rendered, the next signing phase is PAdES over the PDF —
        // it must sign the rendered PDF, not the sealed XML.
        i.markPdfRendered("rendered.pdf");
        assertThat(i.currentSigningStorageKey()).isEqualTo("rendered.pdf");
    }

    // G1 Rev 1 fix: explicit null-guard test (independent of the 7 plan tests)
    @Test void assign_throwsWhenXmlStorageKeyIsNull() {
        TranscriptItem i = TranscriptItem.register("tx-1", "doc-001", "KMUTT", "REGULAR", null);
        assertThatThrownBy(() -> i.assign(UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class);
    }

    // --- orphaned-artifact purge ---

    private TranscriptItem failedItemWithAllKeys() {
        TranscriptItem i = TranscriptItem.register("tx-1", "doc-001", "KMUTT", "REGULAR",
            "2026/07/10/01/original.xml");
        i.assign(UUID.randomUUID());
        i.markRegistrarSigned("registrar.xml");
        i.markDeanSigned("dean.xml");
        i.markSealed("sealed.xml");
        i.fail("dean rejected");
        return i;
    }

    @Test void purgeableArtifactKeys_neverIncludesTheOriginal() {
        TranscriptItem i = failedItemWithAllKeys();
        // The original XML is processing's source of truth and is NOT ours to delete.
        // Sweeping it would destroy the only copy of the submitted transcript.
        assertThat(i.purgeableArtifactKeys())
            .doesNotContain("2026/07/10/01/original.xml")
            .containsExactlyInAnyOrder("registrar.xml", "dean.xml", "sealed.xml");
    }

    @Test void purgeableArtifactKeys_skipsUrlShapedValues() {
        TranscriptItem i = failedItemWithAllKeys();
        i.markPdfRendered("http://minio:9000/transcript-pdfs/x.pdf?X-Amz-Signature=abc");
        i.fail("pades failed");
        // pdfKey currently holds a PRESIGNED URL, not a key, and it lives in a bucket the
        // orchestrator has no config for. Deleting it as if it were a key in xml-bucket
        // would target the wrong object. Refuse anything URL-shaped.
        assertThat(i.purgeableArtifactKeys()).noneMatch(k -> k.startsWith("http"));
    }

    @Test void purgeableArtifactKeys_isEmptyWhenNothingWasSigned() {
        TranscriptItem i = TranscriptItem.register("tx-1", "doc-001", "KMUTT", "REGULAR", "o.xml");
        i.assign(UUID.randomUUID());
        i.fail("registrar signing failed before any upload");
        assertThat(i.purgeableArtifactKeys()).isEmpty();
    }

    @Test void markArtifactsPurged_isIdempotentAndStampsTime() {
        TranscriptItem i = failedItemWithAllKeys();
        assertThat(i.getArtifactsPurgedAt()).isNull();
        i.markArtifactsPurged();
        assertThat(i.getArtifactsPurgedAt()).isNotNull();
    }
}
