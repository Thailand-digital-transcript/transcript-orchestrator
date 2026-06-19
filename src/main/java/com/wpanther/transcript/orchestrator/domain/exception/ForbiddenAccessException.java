package com.wpanther.transcript.orchestrator.domain.exception;

/**
 * An authenticated principal lacks the role or claim required to act. Maps to
 * HTTP 403. Used by {@code ApprovalController} when a caller has no approver
 * role or no {@code institution_code} claim — these are misconfigured-but-
 * authenticated principals, not malformed request bodies (spec §4.4 / §7).
 */
public class ForbiddenAccessException extends RuntimeException {
    public ForbiddenAccessException(String message) { super(message); }
}
