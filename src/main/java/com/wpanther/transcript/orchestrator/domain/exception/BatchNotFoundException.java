package com.wpanther.transcript.orchestrator.domain.exception;
public class BatchNotFoundException extends RuntimeException {
    public BatchNotFoundException(String id) { super("Batch not found: " + id); }
}
