package com.wpanther.transcript.orchestrator.domain.exception;

/**
 * A transcript item id was not found, or is owned by a different institution
 * than the authenticated caller. Maps to HTTP 404. The two cases deliberately
 * share one exception and one message so a cross-institution caller cannot
 * distinguish "not yours" from "does not exist" (privacy: spec §4.4).
 */
public class TranscriptItemNotFoundException extends RuntimeException {
    public TranscriptItemNotFoundException(String message) { super(message); }
}
