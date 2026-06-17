package com.wpanther.transcript.orchestrator.domain.exception;
public class InstitutionMismatchException extends RuntimeException {
    public InstitutionMismatchException(String batch, String approval) {
        super("Institution mismatch: batch=" + batch + " approval=" + approval);
    }
}
