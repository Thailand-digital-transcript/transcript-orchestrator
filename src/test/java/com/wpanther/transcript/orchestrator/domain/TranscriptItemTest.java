package com.wpanther.transcript.orchestrator.domain;

import com.wpanther.transcript.orchestrator.domain.model.*;
import com.wpanther.transcript.orchestrator.domain.service.TranscriptKeyResolver;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TranscriptItemTest {
    private final TranscriptKeyResolver resolver =
            new TranscriptKeyResolver("transcripts", "signed-transcripts", "transcript-pdfs");

    private static final String ORIG = "2026/07/10/01/transcript-90993829998.xml";

    private TranscriptItem itemAt(ItemStatus target) {
        TranscriptItem i = TranscriptItem.register("90993829998", "doc-1", "KMUTT", "01", ORIG);
        if (target == ItemStatus.REGISTERED) return i;
        i.assign(UUID.randomUUID());
        if (target == ItemStatus.ASSIGNED) return i;
        i.markRegistrarSigned("2026/07/10/01/transcript-90993829998.registrar.xml");
        if (target == ItemStatus.REGISTRAR_SIGNED) return i;
        i.markDeanSigned("2026/07/10/01/transcript-90993829998.dean.xml");
        if (target == ItemStatus.DEAN_SIGNED) return i;
        i.markSealed("2026/07/10/01/transcript-90993829998.sealed.xml");
        if (target == ItemStatus.SEALED) return i;
        i.markPdfRendered("2026/07/10/01/transcript-90993829998.pdf");
        return i;   // PDF_RENDERED
    }

    @Test
    void nextSigningSource_walksTheChain() {
        assertThat(itemAt(ItemStatus.ASSIGNED).nextSigningSource(resolver))
                .isEqualTo(new StorageRef("transcripts", ORIG));
        assertThat(itemAt(ItemStatus.REGISTRAR_SIGNED).nextSigningSource(resolver))
                .isEqualTo(new StorageRef("signed-transcripts",
                        "2026/07/10/01/transcript-90993829998.registrar.xml"));
        assertThat(itemAt(ItemStatus.DEAN_SIGNED).nextSigningSource(resolver))
                .isEqualTo(new StorageRef("signed-transcripts",
                        "2026/07/10/01/transcript-90993829998.dean.xml"));
        assertThat(itemAt(ItemStatus.SEALED).nextSigningSource(resolver))
                .isEqualTo(new StorageRef("signed-transcripts",
                        "2026/07/10/01/transcript-90993829998.sealed.xml"));
    }

    /**
     * The regression test for the confirmed PDF_RENDERED bug. The old single method
     * returned pdfKey here to BOTH callers, so /xml and /content presigned a PDF key
     * against the XML bucket. The signer wants the PDF; the viewer wants the sealed XML.
     */
    @Test
    void atPdfRendered_theSignerWantsThePdf_butTheViewerWantsTheSealedXml() {
        TranscriptItem item = itemAt(ItemStatus.PDF_RENDERED);

        assertThat(item.nextSigningSource(resolver))
                .as("the PAdES round signs the rendered PDF")
                .isEqualTo(new StorageRef("transcript-pdfs",
                        "2026/07/10/01/transcript-90993829998.pdf"));

        assertThat(item.latestXmlRef(resolver))
                .as("the viewer must never be handed the PDF as though it were XML")
                .isEqualTo(new StorageRef("signed-transcripts",
                        "2026/07/10/01/transcript-90993829998.sealed.xml"));
    }

    @Test
    void latestXmlRef_neverReturnsThePdf_atAnyStatus() {
        for (ItemStatus s : List.of(ItemStatus.ASSIGNED, ItemStatus.REGISTRAR_SIGNED,
                ItemStatus.DEAN_SIGNED, ItemStatus.SEALED, ItemStatus.PDF_RENDERED)) {
            assertThat(itemAt(s).latestXmlRef(resolver).key())
                    .as("status " + s)
                    .endsWith(".xml");
        }
    }

    /**
     * Documents the hazard that {@code TranscriptItemController}'s null-key guard exists
     * to prevent: a REGISTERED item with no XML uploaded yet (originalXmlStorageKey still
     * null — the column has no NOT NULL constraint, and {@code assign()} is the only place
     * that guards against it) makes both new methods throw rather than return a ref. Callers
     * that reach a REGISTERED item (i.e. never went through {@code assign()}) must check
     * {@code getOriginalXmlStorageKey() == null} themselves before calling either method.
     */
    @Test
    void latestXmlRef_throwsOnNullOriginalKey_forAnUnassignedItem() {
        TranscriptItem i = TranscriptItem.register("90993829998", "doc-1", "KMUTT", "01", null);
        assertThatThrownBy(() -> i.latestXmlRef(resolver))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> i.nextSigningSource(resolver))
                .isInstanceOf(IllegalArgumentException.class);
    }

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
