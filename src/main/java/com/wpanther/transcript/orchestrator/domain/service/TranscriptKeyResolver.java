package com.wpanther.transcript.orchestrator.domain.service;

import com.wpanther.transcript.orchestrator.domain.model.StorageRef;

/**
 * The ONLY class in the platform that builds an object key. Every artifact of a transcript
 * is derived from the original key that processing minted — never independently constructed.
 *
 * <p>This is why signing and pdf-generation stop naming objects. Neither can build the
 * prefix: typeCode stops at the orchestrator boundary (it is on TranscriptItem but on no
 * command DTO), and no downstream service has the ingest date at all. A per-service
 * LocalDate.now() would scatter one transcript across several date folders, because the
 * registrar and dean approvals are manual and routinely land on a later day than ingest.
 */
public class TranscriptKeyResolver {

    private static final String XML_SUFFIX = ".xml";

    private final String originalBucket;
    private final String signedBucket;
    private final String pdfBucket;

    public TranscriptKeyResolver(String originalBucket, String signedBucket, String pdfBucket) {
        this.originalBucket = originalBucket;
        this.signedBucket = signedBucket;
        this.pdfBucket = pdfBucket;
    }

    public StorageRef original(String originalKey)       { return new StorageRef(originalBucket, requireStem(originalKey) + XML_SUFFIX); }
    public StorageRef registrarSigned(String originalKey) { return new StorageRef(signedBucket, requireStem(originalKey) + ".registrar.xml"); }
    public StorageRef deanSigned(String originalKey)      { return new StorageRef(signedBucket, requireStem(originalKey) + ".dean.xml"); }
    public StorageRef sealed(String originalKey)          { return new StorageRef(signedBucket, requireStem(originalKey) + ".sealed.xml"); }
    public StorageRef renderedPdf(String originalKey)     { return new StorageRef(pdfBucket, requireStem(originalKey) + ".pdf"); }
    public StorageRef sealedPdf(String originalKey)       { return new StorageRef(signedBucket, requireStem(originalKey) + ".sealed.pdf"); }

    /**
     * Strips the trailing ".xml", leaving "<yyyy>/<MM>/<dd>/<typeCode>/transcript-<id>".
     * Throws rather than guessing: a malformed original key must not silently yield a
     * garbage sibling sitting next to a real transcript.
     */
    private String requireStem(String originalKey) {
        if (originalKey == null || !originalKey.endsWith(XML_SUFFIX)) {
            throw new IllegalArgumentException(
                    "originalXmlStorageKey must be a '.xml' key of the form "
                            + "<yyyy>/<MM>/<dd>/<typeCode>/transcript-<id>.xml, but was: " + originalKey);
        }
        String stem = originalKey.substring(0, originalKey.length() - XML_SUFFIX.length());
        if (!stem.matches("\\d{4}/\\d{2}/\\d{2}/[^/]+/transcript-[^/]+")) {
            throw new IllegalArgumentException(
                    "originalXmlStorageKey must be a '.xml' key of the form "
                            + "<yyyy>/<MM>/<dd>/<typeCode>/transcript-<id>.xml, but was: " + originalKey);
        }
        return stem;
    }
}
