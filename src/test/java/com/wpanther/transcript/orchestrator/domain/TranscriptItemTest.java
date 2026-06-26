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
}
