package com.wpanther.transcript.orchestrator.domain;

import com.wpanther.transcript.orchestrator.domain.model.StorageRef;
import com.wpanther.transcript.orchestrator.domain.service.TranscriptKeyResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptKeyResolverTest {

    private static final String ORIGINAL = "2026/07/10/01/transcript-90993829998.xml";

    private final TranscriptKeyResolver resolver =
            new TranscriptKeyResolver("transcripts", "signed-transcripts", "transcript-pdfs");

    @Test
    void derivesEveryArtifactFromTheOriginalKey() {
        assertThat(resolver.original(ORIGINAL))
                .isEqualTo(new StorageRef("transcripts", ORIGINAL));
        assertThat(resolver.registrarSigned(ORIGINAL)).isEqualTo(new StorageRef(
                "signed-transcripts", "2026/07/10/01/transcript-90993829998.registrar.xml"));
        assertThat(resolver.deanSigned(ORIGINAL)).isEqualTo(new StorageRef(
                "signed-transcripts", "2026/07/10/01/transcript-90993829998.dean.xml"));
        assertThat(resolver.sealed(ORIGINAL)).isEqualTo(new StorageRef(
                "signed-transcripts", "2026/07/10/01/transcript-90993829998.sealed.xml"));
        assertThat(resolver.renderedPdf(ORIGINAL)).isEqualTo(new StorageRef(
                "transcript-pdfs", "2026/07/10/01/transcript-90993829998.pdf"));
        assertThat(resolver.sealedPdf(ORIGINAL)).isEqualTo(new StorageRef(
                "signed-transcripts", "2026/07/10/01/transcript-90993829998.sealed.pdf"));
    }

    @Test
    void preservesTheIngestPrefixVerbatim_includingUnknownTypeCode() {
        // The prefix is the ingest date, captured once by processing. Deriving it again
        // here would scatter one transcript across several date folders, because the
        // registrar and dean approvals are manual and routinely land on a later day.
        String unknown = "2026/07/10/UNKNOWN/transcript-90993829998.xml";
        assertThat(resolver.sealed(unknown).key())
                .isEqualTo("2026/07/10/UNKNOWN/transcript-90993829998.sealed.xml");
    }

    @Test
    void rejectsAMalformedOriginalKey() {
        // Must THROW, not silently produce a garbage sibling next to a real transcript.
        assertThatThrownBy(() -> resolver.sealed("transcript-90993829998"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("originalXmlStorageKey");
        assertThatThrownBy(() -> resolver.sealed("2026/07/10/01/transcript-90993829998.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.sealed(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
